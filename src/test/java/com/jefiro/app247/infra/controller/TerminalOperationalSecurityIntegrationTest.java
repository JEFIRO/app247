package com.jefiro.app247.infra.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.dto.TerminalTelemetryRequest;
import com.jefiro.app247.domain.model.enum_type.TerminalLifecycleState;
import com.jefiro.app247.domain.model.enum_type.TerminalStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.enum_type.OriginRequest;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.repository.CarrinhoRepository;
import com.jefiro.app247.infra.repository.OrderRepository;
import com.jefiro.app247.infra.repository.CondominioRepository;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import com.jefiro.app247.infra.service.EmpresaContext;
import com.jefiro.app247.infra.service.TerminalRequestAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TerminalOperationalSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EmpresaRepository empresas;
    @Autowired CondominioRepository condominios;
    @Autowired TerminalRepository terminals;
    @Autowired TerminalRequestAuthorizationService authorization;
    @Autowired CarrinhoRepository carts;
    @Autowired OrderRepository orders;

    private Terminal terminalA;
    private Terminal terminalA2;
    private Terminal terminalB;
    private String credentialA;
    private String credentialA2;

    @BeforeEach
    void setup() {
        Empresa empresaA = empresa("A");
        Empresa empresaB = empresa("B");
        Condominio condominioA = condominio(empresaA, "A");
        terminalA = terminal(condominioA, "A1");
        terminalA2 = terminal(condominioA, "A2");
        terminalB = terminal(condominio(empresaB, "B"), "B");
        EmpresaContext.set(empresaA.getId());
        credentialA = authorization.provision(terminalA.getIdTerminal()).credential();
        credentialA2 = authorization.provision(terminalA2.getIdTerminal()).credential();
        EmpresaContext.clear();
    }

    @Test
    void syncExigeCredencialIndividualECorrespondenciaExata() throws Exception {
        mvc.perform(get("/produtos/sync").param("uuidTerminal", terminalA.getIdTerminal()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mvc.perform(get("/produtos/sync").header("X-Terminal-Token", "invalid")
                        .param("uuidTerminal", terminalA.getIdTerminal()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TERMINAL_AUTH_INVALID"));
        mvc.perform(get("/produtos/sync").header("X-Terminal-Token", credentialA)
                        .param("uuidTerminal", terminalA.getIdTerminal()))
                .andExpect(status().isOk());
        mvc.perform(get("/produtos/sync").header("X-Terminal-Token", credentialA)
                        .param("uuidTerminal", terminalA2.getIdTerminal()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/produtos/sync").header("X-Terminal-Token", credentialA)
                        .param("uuidTerminal", terminalB.getIdTerminal()))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogoDoTerminalNaoAceitaOutroDispositivo() throws Exception {
        mvc.perform(get("/terminais/{id}/produtos-disponiveis", terminalA.getIdTerminal())
                        .header("X-Terminal-Token", credentialA))
                .andExpect(status().isOk());
        mvc.perform(get("/terminais/{id}/produtos-disponiveis", terminalA2.getIdTerminal())
                        .header("X-Terminal-Token", credentialA))
                .andExpect(status().isForbidden());
    }

    @Test
    void telemetriaNaoAceitaTerminalDoPayloadDiferente() throws Exception {
        mvc.perform(post("/terminal/telemetry").header("X-Terminal-Token", credentialA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.copy().findAndRegisterModules()
                                .writeValueAsBytes(telemetry(terminalA.getIdTerminal()))))
                .andExpect(status().isAccepted());
        mvc.perform(post("/terminal/telemetry").header("X-Terminal-Token", credentialA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.copy().findAndRegisterModules()
                                .writeValueAsBytes(telemetry(terminalB.getIdTerminal()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void lifecycleValidaPathMesmoDuranteBootstrap() throws Exception {
        mvc.perform(get("/terminal/{id}/bootstrap", terminalA.getIdTerminal())
                        .header("X-Terminal-Token", credentialA))
                .andExpect(status().isOk());
        mvc.perform(get("/terminal/{id}/bootstrap", terminalB.getIdTerminal())
                        .header("X-Terminal-Token", credentialA))
                .andExpect(status().isForbidden());
    }

    @Test
    void terminalDesativadoNaoPodeSincronizar() throws Exception {
        terminalA.setAtivo(false);
        terminals.saveAndFlush(terminalA);
        mvc.perform(get("/produtos/sync").header("X-Terminal-Token", credentialA)
                        .param("uuidTerminal", terminalA.getIdTerminal()))
                .andExpect(status().isForbidden());
    }

    @Test
    void factoryResetSoAceitaTerminalEmResetRequired() throws Exception {
        mvc.perform(post("/terminal/{id}/factory-reset/started", terminalA.getIdTerminal())
                        .header("X-Terminal-Token", credentialA))
                .andExpect(status().isForbidden());

        terminalA.setAtivo(false);
        terminalA.setLifecycleState(TerminalLifecycleState.RESET_REQUIRED);
        terminals.saveAndFlush(terminalA);
        mvc.perform(post("/terminal/{id}/factory-reset/started", terminalA.getIdTerminal())
                        .header("X-Terminal-Token", credentialA))
                .andExpect(status().isNoContent());
    }

    @Test
    void carrinhoEOOrderExigemOwnershipExatoDoTerminal() throws Exception {
        Carrinho cartA = cart(terminalA);
        Carrinho cartA2 = cart(terminalA2);
        Carrinho cartB = cart(terminalB);
        mvc.perform(get("/carrinho/{id}", cartA.getIdCarrinho())
                        .header("X-Terminal-Token", credentialA)).andExpect(status().isOk());
        mvc.perform(get("/carrinho/{id}", cartA2.getIdCarrinho())
                        .header("X-Terminal-Token", credentialA)).andExpect(status().isForbidden());
        mvc.perform(get("/carrinho/{id}", cartB.getIdCarrinho())
                        .header("X-Terminal-Token", credentialA)).andExpect(status().isForbidden());

        Order own = order(cartA);
        Order other = order(cartA2);
        Order crossTenant = order(cartB);
        mvc.perform(get("/order").param("carrinho_id", own.getIdOrder())
                        .header("X-Terminal-Token", credentialA)).andExpect(status().isOk());
        mvc.perform(get("/order").param("carrinho_id", other.getIdOrder())
                        .header("X-Terminal-Token", credentialA)).andExpect(status().isForbidden());
        mvc.perform(get("/order").param("carrinho_id", crossTenant.getIdOrder())
                        .header("X-Terminal-Token", credentialA)).andExpect(status().isForbidden());

        mvc.perform(get("/checkout/carrinho").param("idCarrinho", cartA2.getIdCarrinho())
                        .header("X-Terminal-Token", credentialA)).andExpect(status().isForbidden());
        mvc.perform(get("/checkout/carrinho").param("idCarrinho", cartB.getIdCarrinho())
                        .header("X-Terminal-Token", credentialA)).andExpect(status().isForbidden());
    }

    @Test
    void dadosDoComprovanteExigemTerminalDaCredencialEDaOrder() throws Exception {
        Order own = order(cart(terminalA));
        Order other = order(cart(terminalA2));
        mvc.perform(get("/comprovante/{id}", own.getIdOrder()).param("terminalId", terminalA.getIdTerminal())
                        .header("X-Terminal-Token", credentialA))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/comprovante/{id}", other.getIdOrder()).param("terminalId", terminalA2.getIdTerminal())
                        .header("X-Terminal-Token", credentialA))
                .andExpect(status().isForbidden());
        mvc.perform(get("/comprovante/{id}", other.getIdOrder()).param("terminalId", terminalA.getIdTerminal())
                        .header("X-Terminal-Token", credentialA))
                .andExpect(status().isNotFound());
    }

    @Test
    void comprovanteNaoAceitaTerminalDoPayloadDiferente() throws Exception {
        mvc.perform(post("/comprovante").header("X-Terminal-Token", credentialA)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"terminalId":"%s","pedidoId":"order-b","tipoEnvio":"EMAIL",
                         "destinatario":"cliente@example.test"}
                        """.formatted(terminalA2.getIdTerminal())))
                .andExpect(status().isForbidden());
    }

    private TerminalTelemetryRequest telemetry(String terminalId) {
        return new TerminalTelemetryRequest(terminalId, Instant.now(),
                new TerminalTelemetryRequest.SystemMetrics(1L, 1d, 40d, 1L, 2L, 50d,
                        1L, 2L, 50d, 0d, false, false, false, false,
                        false, false, false, false, "0x0"),
                new TerminalTelemetryRequest.NetworkMetrics(true, "eth0", null, "127.0.0.1",
                        100, "EXCELLENT", true, 1L),
                new TerminalTelemetryRequest.ApplicationMetrics("test", 1L, "CONNECTED",
                        null, null, null, null, false, false),
                new TerminalTelemetryRequest.DisplayMetrics(1920, 1080, "HORIZONTAL"));
    }

    private Empresa empresa(String suffix) {
        Empresa empresa = new Empresa();
        empresa.setRazaoSocial("Empresa " + suffix);
        empresa.setNomeFantasia("Empresa " + suffix);
        empresa.setCnpj(UUID.randomUUID().toString().substring(0, 18));
        empresa.setEmail(UUID.randomUUID() + "@example.test");
        return empresas.saveAndFlush(empresa);
    }

    private Condominio condominio(Empresa empresa, String suffix) {
        Condominio condominio = new Condominio();
        condominio.setNome("Condominio " + suffix);
        condominio.setEmpresa(empresa);
        return condominios.saveAndFlush(condominio);
    }

    private Terminal terminal(Condominio condominio, String suffix) {
        Terminal terminal = new Terminal();
        terminal.setCondominio(condominio);
        terminal.setNome("Terminal " + suffix);
        terminal.setCodigo("terminal-" + suffix + "-" + UUID.randomUUID());
        terminal.setAtivo(true);
        terminal.setStatus(TerminalStatus.OFFLINE);
        terminal.setLifecycleState(TerminalLifecycleState.ACTIVE);
        return terminals.saveAndFlush(terminal);
    }

    private Carrinho cart(Terminal terminal) {
        Carrinho cart = new Carrinho();
        cart.setTerminal(terminal);
        cart.setSubtotal(java.math.BigDecimal.TEN);
        return carts.saveAndFlush(cart);
    }

    private Order order(Carrinho cart) {
        Order order = new Order(cart);
        order.setStatus(OrderStatus.PENDING);
        order.setOriginRequest(OriginRequest.TERMINAL);
        order.setSubtotal(java.math.BigDecimal.TEN);
        order.setSubtotalPosPromocoes(java.math.BigDecimal.TEN);
        order.setDescontoPromocoes(java.math.BigDecimal.ZERO);
        order.setDescontoCupom(java.math.BigDecimal.ZERO);
        order.setDesconto(java.math.BigDecimal.ZERO);
        order.setTotalCalculado(java.math.BigDecimal.TEN);
        order.setTotalCobrado(java.math.BigDecimal.TEN);
        return orders.saveAndFlush(order);
    }
}
