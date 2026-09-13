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
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String SENT_PREFIX = "SENT:";

    private final ComprovanteSnapshotService snapshotService;
    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;
    private final URI endpoint;
    private final Duration processingTtl;
    private final Duration successTtl;
    private final String internalToken;

    @Autowired
    public ComprovanteService(
            ComprovanteSnapshotService snapshotService,
            @Qualifier("comprovanteRestTemplate") RestTemplate restTemplate,
            StringRedisTemplate redisTemplate,
            @Value("${app.comprovante.base-url:http://localhost:8000}") String baseUrl,
            @Value("${app.comprovante.endpoint:/comprovante}") String endpointPath,
            @Value("${app.comprovante.idempotency.processing-ttl:PT2M}") Duration processingTtl,
            @Value("${app.comprovante.idempotency.success-ttl:P1D}") Duration successTtl,
            @Value("${app.comprovante.internal-token:}") String internalToken) {
        this.snapshotService = snapshotService;
        this.restTemplate = restTemplate;
        this.redisTemplate = redisTemplate;
        this.endpoint = buildEndpoint(baseUrl, endpointPath);
        this.processingTtl = processingTtl;
        this.successTtl = successTtl;
        this.internalToken = internalToken == null ? "" : internalToken;
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
                        receipt.pedido(), channel.name(), null);
            }
            throw new ApiBusinessException(
                    HttpStatus.CONFLICT,
                    "COMPROVANTE_ALREADY_PROCESSING",
                    "O envio deste comprovante já está em processamento"
            );
        }

        String maskedDestination = channel.mask(destination);
        log.info("[COMPROVANTE] requestId={} orderId={} terminalId={} canal={} destinatarioMascarado={} status=REQUESTED",
                idempotencyId, receipt.pedido(), input.terminalId(), channel, maskedDestination);

        try {
            FastApiComprovanteResponse response = callFastApi(
                    new EnviarComprovanteRequest(
                            idempotencyId, channel.name(), receipt, destination), idempotencyId);
            markSent(redisKey);
            log.info("[COMPROVANTE] requestId={} orderId={} terminalId={} canal={} status=ACCEPTED",
                    idempotencyId, receipt.pedido(), input.terminalId(), channel);
            return ComprovanteEnvioResponse.sent(
                    receipt.pedido(), channel.name(), null);
        } catch (HttpClientErrorException exception) {
            release(redisKey, leaseValue);
            throw integrationFailure(idempotencyId, receipt.pedido(), channel,
                    "FASTAPI_REJECTED", exception, false);
        } catch (HttpServerErrorException exception) {
            // O n8n pode ter recebido a imagem antes de uma resposta 5xx/timeout.
            // Mantemos o lease até expirar para impedir repetição imediata.
            boolean gatewayTimeout = exception.getStatusCode().value() == 504;
            throw integrationFailure(idempotencyId, receipt.pedido(), channel,
                    gatewayTimeout ? "FASTAPI_OR_N8N_TIMEOUT" : "FASTAPI_OR_N8N_ERROR",
                    exception, gatewayTimeout);
        } catch (ResourceAccessException exception) {
            if (causedBy(exception, ConnectException.class)) {
                release(redisKey, leaseValue);
                throw integrationFailure(idempotencyId, receipt.pedido(), channel,
                        "FASTAPI_UNAVAILABLE", exception, false);
            }
            String reason = causedBy(exception, SocketTimeoutException.class)
                    ? "FASTAPI_TIMEOUT" : "FASTAPI_IO_ERROR";
            throw integrationFailure(idempotencyId, receipt.pedido(), channel, reason, exception,
                    causedBy(exception, SocketTimeoutException.class));
        } catch (RestClientException exception) {
            throw integrationFailure(idempotencyId, receipt.pedido(), channel,
                    "FASTAPI_INVALID_RESPONSE", exception, false);
        }
    }

    private FastApiComprovanteResponse callFastApi(
            EnviarComprovanteRequest request,
            String idempotencyId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(IDEMPOTENCY_HEADER, idempotencyId);
        headers.set(CORRELATION_HEADER, idempotencyId);
        if (!internalToken.isBlank()) {
            headers.set(INTERNAL_TOKEN_HEADER, internalToken);
        }

        ResponseEntity<FastApiComprovanteResponse> response = restTemplate.exchange(
                endpoint,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                FastApiComprovanteResponse.class
        );
        FastApiComprovanteResponse body = response.getBody();
        if (body == null
                || !body.success()
                || !"ACCEPTED".equalsIgnoreCase(body.status())
                || !request.request().pedido().equals(body.pedido())
                || !request.canal().equalsIgnoreCase(body.canal())
                || !request.requestId().equals(body.requestId())) {
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
            String existing = redisTemplate.opsForValue().get(key);
            if (existing != null) {
                return existing;
            }

            // A chave pode expirar entre o SET NX e o GET. Tenta adquirir uma
            // única vez; se outra thread ganhar, responde como processamento.
            acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, leaseValue, processingTtl);
            if (Boolean.TRUE.equals(acquired)) {
                return null;
            }
            existing = redisTemplate.opsForValue().get(key);
            return existing != null ? existing : "PROCESSING:unknown";
        } catch (DataAccessException exception) {
            throw new ApiBusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "COMPROVANTE_IDEMPOTENCY_UNAVAILABLE",
                    "Não foi possível iniciar o envio do comprovante com segurança"
            );
        }
    }

    private void markSent(String key) {
        try {
            redisTemplate.opsForValue().set(key, SENT_PREFIX + "ACCEPTED", successTtl);
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
            String requestId,
            String orderId,
            DeliveryChannel channel,
            String reason,
            Exception exception,
            boolean timeout) {
        log.error("[COMPROVANTE] requestId={} orderId={} canal={} status=FAILED reason={}",
                requestId, orderId, channel, reason, exception);
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
