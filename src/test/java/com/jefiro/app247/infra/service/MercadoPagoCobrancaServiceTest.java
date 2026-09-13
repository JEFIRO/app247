package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.dto.mercadopago.OrderRequest;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import com.jefiro.app247.infra.exception.ExternalFailureType;
import com.jefiro.app247.infra.exception.ExternalServiceException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MercadoPagoCobrancaServiceTest {
    @Mock OauthMercadoPagoService oauthService;
    @Mock RestTemplate restTemplate;
    @Mock OrderService orderService;
    @Mock TerminalRepository terminalRepository;
    @Mock MercadoPagoOperationalConfigurationService configurationService;

    @AfterEach
    void limparContexto() {
        EmpresaContext.clear();
    }

    @Test
    void usaTokenDaEmpresaETerminalMercadoPagoDoTerminalInterno() {
        Empresa empresa = Empresa.builder().id("empresa-b").build();
        Order order = new Order();
        order.setIdOrder("order-b");
        order.setEmpresa(empresa);
        order.setIdTerminal("terminal-interno-b");
        order.setTotal(new BigDecimal("25.00"));
        order.setPagamento(attempt(order));
        Terminal terminal = new Terminal();
        terminal.setIdTerminal("terminal-interno-b");
        terminal.setMercadoPagoTerminalId("NEWLAND-B");
        when(terminalRepository.findByIdTerminalAndCondominioEmpresaId("terminal-interno-b", "empresa-b"))
                .thenReturn(Optional.of(terminal));
        when(configurationService.requireConfigured(terminal, "empresa-b"))
                .thenReturn(MercadoPagoConta.builder().accessToken("token-empresa-b").build());
        ArgumentCaptor<HttpEntity<OrderRequest>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), captor.capture(),
                eq(com.jefiro.app247.domain.model.dto.OrderResponse.class)))
                .thenThrow(new RuntimeException("interrompe após capturar request"));

        assertThrows(RuntimeException.class, () -> service().newOrder(order));

        HttpEntity<OrderRequest> entity = captor.getValue();
        assertEquals("Bearer token-empresa-b", entity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("NEWLAND-B", entity.getBody().config().point().terminalId());
        verify(configurationService).requireConfigured(terminal, "empresa-b");
    }

    @Test
    void rejeitaTerminalInternoDeOutraEmpresaAntesDeObterCredencial() {
        Order order = new Order();
        order.setIdOrder("order-a");
        order.setEmpresa(Empresa.builder().id("empresa-a").build());
        order.setIdTerminal("terminal-b");
        order.setPagamento(attempt(order));
        when(terminalRepository.findByIdTerminalAndCondominioEmpresaId("terminal-b", "empresa-a"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service().newOrder(order));
        verifyNoInteractions(oauthService, restTemplate);
    }

    @Test
    void respostaInicialCreatedEhRetornadaEUsaExpiracaoDeQuinzeMinutos() throws Exception {
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        Order order = new Order();
        order.setIdOrder("order-a");
        order.setEmpresa(empresa);
        order.setIdTerminal("terminal-a");
        order.setTotal(new BigDecimal("25.5"));
        order.setPagamento(attempt(order));
        Terminal terminal = new Terminal();
        terminal.setMercadoPagoTerminalId("POINT-A");
        when(terminalRepository.findByIdTerminalAndCondominioEmpresaId("terminal-a", "empresa-a"))
                .thenReturn(Optional.of(terminal));
        when(configurationService.requireConfigured(terminal, "empresa-a"))
                .thenReturn(MercadoPagoConta.builder().accessToken("token-a").build());
        var response = new ObjectMapper().readValue("""
                {
                  "id":"ORD-MP-A",
                  "type":"point",
                  "external_reference":"attempt-order-a",
                  "status":"created",
                  "status_detail":"created"
                }
                """, com.jefiro.app247.domain.model.dto.OrderResponse.class);
        ArgumentCaptor<HttpEntity<OrderRequest>> request = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), request.capture(),
                eq(com.jefiro.app247.domain.model.dto.OrderResponse.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(response));
        var returned = service().newOrder(order);

        assertEquals("PT15M", request.getValue().getBody().expirationTime());
        assertEquals("25.50", request.getValue().getBody().transactions().payments().get(0).amount());
        assertEquals("idem-order-a", request.getValue().getHeaders().getFirst("X-Idempotency-Key"));
        assertEquals("ORD-MP-A", returned.id());
        verify(orderService, never()).save(any());
    }

    @Test
    void respostaTerminalAindaEhRetornadaParaPersistirIdentidadeRemota() throws Exception {
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        Order order = new Order();
        order.setIdOrder("order-a");
        order.setEmpresa(empresa);
        order.setIdTerminal("terminal-a");
        order.setTotal(new BigDecimal("25.00"));
        order.setPagamento(attempt(order));
        Terminal terminal = new Terminal();
        terminal.setMercadoPagoTerminalId("POINT-A");
        when(terminalRepository.findByIdTerminalAndCondominioEmpresaId("terminal-a", "empresa-a"))
                .thenReturn(Optional.of(terminal));
        when(configurationService.requireConfigured(terminal, "empresa-a"))
                .thenReturn(MercadoPagoConta.builder().accessToken("token-a").build());
        var response = new ObjectMapper().readValue("""
                {
                  "id":"ORD-MP-A",
                  "type":"point",
                  "external_reference":"attempt-order-a",
                  "status":"processed",
                  "status_detail":"accredited"
                }
                """, com.jefiro.app247.domain.model.dto.OrderResponse.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(com.jefiro.app247.domain.model.dto.OrderResponse.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(response));

        var returned = service().newOrder(order);

        assertEquals("ORD-MP-A", returned.id());
        assertEquals("processed", returned.status());
    }

    @Test
    void statusNovoAindaEhRetornadoParaPersistirIdentidadeRemota() throws Exception {
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        Order order = new Order();
        order.setIdOrder("order-a");
        order.setEmpresa(empresa);
        order.setIdTerminal("terminal-a");
        order.setTotal(new BigDecimal("25.00"));
        order.setPagamento(attempt(order));
        Terminal terminal = new Terminal();
        terminal.setMercadoPagoTerminalId("POINT-A");
        when(terminalRepository.findByIdTerminalAndCondominioEmpresaId("terminal-a", "empresa-a"))
                .thenReturn(Optional.of(terminal));
        when(configurationService.requireConfigured(terminal, "empresa-a"))
                .thenReturn(MercadoPagoConta.builder().accessToken("token-a").build());
        var response = new ObjectMapper().readValue("""
                {
                  "id":"ORD-MP-A",
                  "type":"point",
                  "external_reference":"attempt-order-a",
                  "status":"future_status",
                  "status_detail":"future_detail"
                }
                """, com.jefiro.app247.domain.model.dto.OrderResponse.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(com.jefiro.app247.domain.model.dto.OrderResponse.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(response));

        var returned = service().newOrder(order);

        assertEquals("ORD-MP-A", returned.id());
        assertEquals("future_status", returned.status());
    }

    @Test
    void classificaCobrancaJaAtivaSemExporRespostaDoProvedor() {
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        Order order = new Order();
        order.setIdOrder("order-a");
        order.setEmpresa(empresa);
        order.setIdTerminal("terminal-a");
        order.setTotal(new BigDecimal("25.00"));
        order.setPagamento(attempt(order));
        Terminal terminal = new Terminal();
        terminal.setMercadoPagoTerminalId("POINT-A");
        when(terminalRepository.findByIdTerminalAndCondominioEmpresaId("terminal-a", "empresa-a"))
                .thenReturn(Optional.of(terminal));
        when(configurationService.requireConfigured(terminal, "empresa-a"))
                .thenReturn(MercadoPagoConta.builder().accessToken("token-a").build());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(com.jefiro.app247.domain.model.dto.OrderResponse.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY,
                        "{\"code\":\"already_queued_order_for_terminal\"}".getBytes(), null));

        ExternalServiceException exception = assertThrows(
                ExternalServiceException.class, () -> service().newOrder(order));

        assertEquals(ExternalFailureType.ACTIVE_CHARGE, exception.getFailureType());
        assertFalse(exception.getMessage().contains("already_queued_order_for_terminal"));
    }

    private MercadoPagoCobrancaService service() {
        MercadoPagoCobrancaService service = new MercadoPagoCobrancaService();
        service.oauthMercadoPagoService = oauthService;
        service.restTemplate = restTemplate;
        service.orderService = orderService;
        service.terminalRepository = terminalRepository;
        service.configurationService = configurationService;
        return service;
    }

    private PaymentAttempt attempt(Order order) {
        PaymentAttempt attempt = new PaymentAttempt(order);
        attempt.setIdPagamento("payment-" + order.getIdOrder());
        attempt.setAttemptNumber(1);
        attempt.setExternalReference("attempt-" + order.getIdOrder());
        attempt.setIdempotencyKey("idem-" + order.getIdOrder());
        return attempt;
    }
}
