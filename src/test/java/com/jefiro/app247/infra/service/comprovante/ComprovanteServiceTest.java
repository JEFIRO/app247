package com.jefiro.app247.infra.service.comprovante;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteRequest;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComprovanteServiceTest {
    @Mock ComprovanteSnapshotService snapshotService;
    @Mock RestTemplate restTemplate;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> values;

    private ComprovanteService service;
    private ComprovanteCompraRequest receipt;
    private final AtomicReference<String> lease = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        receipt = new ComprovanteCompraRequest(
                "Mercado Autônomo",
                "12.345.678/0001-90",
                "Rua das Flores, 123",
                "Condomínio X - Feira de Santana/BA",
                "order-1284",
                "12/09/2026",
                "TERM-001",
                "15:40",
                List.of(new ItemComprovanteRequest("Coca-Cola 350ml", 2, "R$ 11,98")),
                "R$ 11,98",
                "PIX",
                "APROVADO",
                ""
        );
        lenient().when(redisTemplate.opsForValue()).thenReturn(values);
        service = new ComprovanteService(
                snapshotService,
                restTemplate,
                redisTemplate,
                "http://localhost:8000/",
                "/comprovante",
                Duration.ofMinutes(2),
                Duration.ofDays(1),
                "internal-secret"
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void enviaEnvelopeJsonRealComTimeoutConfiguradoNoCliente() throws Exception {
        acquireLease();
        when(snapshotService.montar("order-1284", "terminal-id")).thenReturn(receipt);
        when(restTemplate.exchange(
                eq(URI.create("http://localhost:8000/comprovante")),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FastApiComprovanteResponse.class)))
                .thenAnswer(call -> {
                    HttpEntity<EnviarComprovanteRequest> entity = call.getArgument(2);
                    EnviarComprovanteRequest body = entity.getBody();
                    return ResponseEntity.ok(new FastApiComprovanteResponse(
                            true,
                            "order-1284",
                            "WHATSAPP",
                            body.requestId(),
                            "ACCEPTED"));
                });

        ComprovanteEnvioResponse response = service.enviar(request("(75) 99999-9999"));

        assertThat(response.status()).isEqualTo("ENVIADO");
        assertThat(response.duplicado()).isFalse();
        ArgumentCaptor<HttpEntity<EnviarComprovanteRequest>> captor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq(URI.create("http://localhost:8000/comprovante")),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(FastApiComprovanteResponse.class));

        HttpEntity<EnviarComprovanteRequest> entity = captor.getValue();
        assertThat(entity.getHeaders().getContentType().toString()).isEqualTo("application/json");
        assertThat(entity.getHeaders().getFirst("X-Idempotency-Key")).hasSize(64);
        assertThat(entity.getHeaders().getFirst("X-Correlation-Id"))
                .isEqualTo(entity.getHeaders().getFirst("X-Idempotency-Key"));
        assertThat(entity.getHeaders().getFirst("X-Internal-Token"))
                .isEqualTo("internal-secret");
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().destinatario()).isEqualTo("5575999999999");

        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsBytes(entity.getBody()));
        assertThat(json.path("request").path("itens").get(0).path("valor").asText())
                .isEqualTo("R$ 11,98");
        assertThat(json.path("request").path("qr_data").asText()).isEmpty();
        assertThat(json.path("destinatario").asText()).isEqualTo("5575999999999");
        assertThat(json.path("canal").asText()).isEqualTo("WHATSAPP");
        assertThat(json.path("request_id").asText()).hasSize(64);
    }

    @Test
    void chamadaRepetidaRetornaJaEnviadoSemNovoPost() {
        when(snapshotService.montar("order-1284", "terminal-id")).thenReturn(receipt);
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofMinutes(2))))
                .thenReturn(false);
        when(values.get(anyString())).thenReturn("SENT:ACCEPTED");

        ComprovanteEnvioResponse response = service.enviar(request("5575999999999"));

        assertThat(response.status()).isEqualTo("JA_ENVIADO");
        assertThat(response.duplicado()).isTrue();
        assertThat(response.n8nStatus()).isNull();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void chamadaConcorrenteEnquantoPrimeiraProcessaEhBloqueada() {
        when(snapshotService.montar("order-1284", "terminal-id")).thenReturn(receipt);
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofMinutes(2))))
                .thenReturn(false);
        when(values.get(anyString())).thenReturn("PROCESSING:outro-worker");

        assertThatThrownBy(() -> service.enviar(request("5575999999999")))
                .isInstanceOf(ApiBusinessException.class)
                .satisfies(error -> assertThat(((ApiBusinessException) error).getCode())
                        .isEqualTo("COMPROVANTE_ALREADY_PROCESSING"));

        verifyNoInteractions(restTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fastApiOfflineRetornaErroControladoELiberaRetrySeguro() {
        acquireLease();
        when(values.get(anyString())).thenAnswer(call -> lease.get());
        when(snapshotService.montar("order-1284", "terminal-id")).thenReturn(receipt);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(FastApiComprovanteResponse.class)))
                .thenThrow(new ResourceAccessException(
                        "Connection refused", new ConnectException("Connection refused")));

        assertThatThrownBy(() -> service.enviar(request("5575999999999")))
                .isInstanceOf(ApiBusinessException.class)
                .satisfies(error -> assertThat(((ApiBusinessException) error).getCode())
                        .isEqualTo("COMPROVANTE_SEND_FAILED"));

        verify(redisTemplate).delete(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void timeoutMantemLeaseParaEvitarReenvioImediato() {
        acquireLease();
        when(snapshotService.montar("order-1284", "terminal-id")).thenReturn(receipt);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(FastApiComprovanteResponse.class)))
                .thenThrow(new ResourceAccessException(
                        "Read timed out", new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> service.enviar(request("5575999999999")))
                .isInstanceOf(ApiBusinessException.class)
                .satisfies(error -> assertThat(((ApiBusinessException) error).getCode())
                        .isEqualTo("COMPROVANTE_SERVICE_TIMEOUT"));

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void destinatarioInvalidoFalhaAntesDeConsultarPedido() {
        assertThatThrownBy(() -> service.enviar(request("telefone-invalido")))
                .isInstanceOf(ApiBusinessException.class)
                .satisfies(error -> assertThat(((ApiBusinessException) error).getCode())
                        .isEqualTo("COMPROVANTE_INVALID_DESTINATION"));
        verifyNoInteractions(snapshotService, restTemplate);
    }

    private void acquireLease() {
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofMinutes(2))))
                .thenAnswer(call -> {
                    lease.set(call.getArgument(1));
                    return true;
                });
    }

    private ComprovanteRequest request(String destination) {
        return new ComprovanteRequest(
                "terminal-id",
                "order-1284",
                "WHATSAPP",
                destination
        );
    }
}
