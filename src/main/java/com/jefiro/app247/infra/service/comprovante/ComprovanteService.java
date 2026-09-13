package com.jefiro.app247.infra.service.comprovante;

import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteRequest;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class ComprovanteService {
    private static final Logger log = LoggerFactory.getLogger(ComprovanteService.class);
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final String SENT_PREFIX = "SENT:";

    private final ComprovanteSnapshotService snapshotService;
    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;
    private final URI endpoint;
    private final Duration processingTtl;
    private final Duration successTtl;

    @Autowired
    public ComprovanteService(
            ComprovanteSnapshotService snapshotService,
            @Qualifier("comprovanteRestTemplate") RestTemplate restTemplate,
            StringRedisTemplate redisTemplate,
            @Value("${app.comprovante.base-url:http://localhost:8000}") String baseUrl,
            @Value("${app.comprovante.endpoint:/comprovante}") String endpointPath,
            @Value("${app.comprovante.idempotency.processing-ttl:PT2M}") Duration processingTtl,
            @Value("${app.comprovante.idempotency.success-ttl:P1D}") Duration successTtl) {
        this.snapshotService = snapshotService;
        this.restTemplate = restTemplate;
        this.redisTemplate = redisTemplate;
        this.endpoint = buildEndpoint(baseUrl, endpointPath);
        this.processingTtl = processingTtl;
        this.successTtl = successTtl;
    }

    public ComprovanteEnvioResponse enviar(ComprovanteRequest input) {
        DeliveryChannel channel = DeliveryChannel.parse(input.tipoEnvio());
        String destination = channel.normalize(input.destinatario());
        ComprovanteCompraRequest receipt = snapshotService.montar(input.pedidoId(), input.terminalId());

        String idempotencyId = sha256(receipt.pedido() + "|" + channel + "|" + destination);
        String redisKey = "receipt:send:" + idempotencyId;
        String leaseValue = "PROCESSING:" + UUID.randomUUID();

        String existing = acquire(redisKey, leaseValue);
        if (existing != null) {
            if (existing.startsWith(SENT_PREFIX)) {
                return ComprovanteEnvioResponse.duplicate(
                        receipt.pedido(), channel.name(), parseN8nStatus(existing));
            }
            throw new ApiBusinessException(
                    HttpStatus.CONFLICT,
                    "COMPROVANTE_ALREADY_PROCESSING",
                    "O envio deste comprovante já está em processamento"
            );
        }

        String maskedDestination = channel.mask(destination);
        log.info("[COMPROVANTE] orderId={} terminalId={} canal={} destinatarioMascarado={} status=REQUESTED",
                receipt.pedido(), input.terminalId(), channel, maskedDestination);

        try {
            FastApiComprovanteResponse response = callFastApi(
                    new EnviarComprovanteRequest(receipt, destination), idempotencyId);
            markSent(redisKey, response.n8nStatus());
            log.info("[COMPROVANTE] orderId={} terminalId={} canal={} status=SENT n8nStatus={}",
                    receipt.pedido(), input.terminalId(), channel, response.n8nStatus());
            return ComprovanteEnvioResponse.sent(
                    receipt.pedido(), channel.name(), response.n8nStatus());
        } catch (HttpClientErrorException exception) {
            release(redisKey, leaseValue);
            throw integrationFailure(receipt.pedido(), channel, "FASTAPI_REJECTED", exception, false);
        } catch (HttpServerErrorException exception) {
            // O n8n pode ter recebido a imagem antes de uma resposta 5xx/timeout.
            // Mantemos o lease até expirar para impedir repetição imediata.
            throw integrationFailure(receipt.pedido(), channel, "FASTAPI_OR_N8N_ERROR", exception, false);
        } catch (ResourceAccessException exception) {
            if (causedBy(exception, ConnectException.class)) {
                release(redisKey, leaseValue);
                throw integrationFailure(receipt.pedido(), channel, "FASTAPI_UNAVAILABLE", exception, false);
            }
            String reason = causedBy(exception, SocketTimeoutException.class)
                    ? "FASTAPI_TIMEOUT" : "FASTAPI_IO_ERROR";
            throw integrationFailure(receipt.pedido(), channel, reason, exception,
                    causedBy(exception, SocketTimeoutException.class));
        } catch (RestClientException exception) {
            throw integrationFailure(receipt.pedido(), channel, "FASTAPI_INVALID_RESPONSE", exception, false);
        }
    }

    private FastApiComprovanteResponse callFastApi(
            EnviarComprovanteRequest request,
            String idempotencyId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(IDEMPOTENCY_HEADER, idempotencyId);

        ResponseEntity<FastApiComprovanteResponse> response = restTemplate.exchange(
                endpoint,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                FastApiComprovanteResponse.class
        );
        FastApiComprovanteResponse body = response.getBody();
        if (body == null
                || !"enviado".equalsIgnoreCase(body.status())
                || !request.request().pedido().equals(body.pedido())
                || body.n8nStatus() == null
                || body.n8nStatus() < 200
                || body.n8nStatus() >= 300) {
            throw new RestClientException("Resposta inválida do serviço de comprovantes");
        }
        return body;
    }

    /**
     * @return valor existente quando o lease não pôde ser adquirido; null quando adquirido.
     */
    private String acquire(String key, String leaseValue) {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, leaseValue, processingTtl);
            if (Boolean.TRUE.equals(acquired)) {
                return null;
            }
            return redisTemplate.opsForValue().get(key);
        } catch (DataAccessException exception) {
            throw new ApiBusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "COMPROVANTE_IDEMPOTENCY_UNAVAILABLE",
                    "Não foi possível iniciar o envio do comprovante com segurança"
            );
        }
    }

    private void markSent(String key, Integer n8nStatus) {
        try {
            redisTemplate.opsForValue().set(key, SENT_PREFIX + n8nStatus, successTtl);
        } catch (DataAccessException exception) {
            // O envio externo já foi confirmado. A venda e a resposta de sucesso não
            // podem ser revertidas por uma falha posterior ao gravar a deduplicação.
            log.error("[COMPROVANTE] status=IDEMPOTENCY_MARK_FAILED reason=REDIS_UNAVAILABLE");
        }
    }

    private void release(String key, String leaseValue) {
        try {
            if (leaseValue.equals(redisTemplate.opsForValue().get(key))) {
                redisTemplate.delete(key);
            }
        } catch (DataAccessException exception) {
            log.warn("[COMPROVANTE] status=IDEMPOTENCY_RELEASE_FAILED reason=REDIS_UNAVAILABLE");
        }
    }

    private ApiBusinessException integrationFailure(
            String orderId,
            DeliveryChannel channel,
            String reason,
            Exception exception,
            boolean timeout) {
        log.error("[COMPROVANTE] orderId={} canal={} status=FAILED reason={}",
                orderId, channel, reason, exception);
        if (timeout) {
            return new ApiBusinessException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "COMPROVANTE_SERVICE_TIMEOUT",
                    "O serviço de comprovantes demorou para responder. Verifique o status antes de repetir"
            );
        }
        return new ApiBusinessException(
                HttpStatus.BAD_GATEWAY,
                "COMPROVANTE_SEND_FAILED",
                "Não foi possível gerar ou enviar o comprovante agora"
        );
    }

    private static int parseN8nStatus(String stored) {
        try {
            return Integer.parseInt(stored.substring(SENT_PREFIX.length()));
        } catch (RuntimeException ignored) {
            return 200;
        }
    }

    private static URI buildEndpoint(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("app.comprovante.base-url não configurada");
        }
        String normalizedBase = baseUrl.replaceAll("/+$", "");
        String normalizedPath = path == null ? "" : path.replaceAll("^/+", "");
        return URI.create(normalizedBase + "/" + normalizedPath);
    }

    private static boolean causedBy(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponível", impossible);
        }
    }

    enum DeliveryChannel {
        WHATSAPP {
            @Override
            String normalize(String value) {
                String candidate = requireDestination(value);
                if (!candidate.matches("[+()\\-\\s0-9]+")) {
                    throw invalidDestination("Telefone possui caracteres inválidos");
                }
                String digits = candidate.replaceAll("\\D", "");
                if (digits.length() == 10 || digits.length() == 11) {
                    digits = "55" + digits;
                }
                if (!digits.startsWith("55") || (digits.length() != 12 && digits.length() != 13)) {
                    throw invalidDestination("Telefone deve estar no formato brasileiro com DDI e DDD");
                }
                return digits;
            }

            @Override
            String mask(String value) {
                return "*".repeat(Math.max(0, value.length() - 4))
                        + value.substring(Math.max(0, value.length() - 4));
            }
        },
        EMAIL {
            @Override
            String normalize(String value) {
                String candidate = requireDestination(value).toLowerCase(Locale.ROOT);
                if (!candidate.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                    throw invalidDestination("E-mail inválido");
                }
                return candidate;
            }

            @Override
            String mask(String value) {
                int at = value.indexOf('@');
                return at <= 1 ? "***" + value.substring(Math.max(at, 0))
                        : value.charAt(0) + "***" + value.substring(at);
            }
        };

        abstract String normalize(String value);

        abstract String mask(String value);

        static DeliveryChannel parse(String raw) {
            try {
                return valueOf(raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw invalidDestination("tipoEnvio deve ser WHATSAPP ou EMAIL");
            }
        }

        static String requireDestination(String raw) {
            if (raw == null || raw.isBlank()) {
                throw invalidDestination("Destinatário não informado");
            }
            return raw.trim();
        }

        static ApiBusinessException invalidDestination(String message) {
            return new ApiBusinessException(
                    HttpStatus.BAD_REQUEST,
                    "COMPROVANTE_INVALID_DESTINATION",
                    message
            );
        }
    }
}
