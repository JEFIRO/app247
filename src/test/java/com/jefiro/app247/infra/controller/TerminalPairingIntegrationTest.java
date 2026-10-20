package com.jefiro.app247.infra.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.TerminalLifecycleState;
import com.jefiro.app247.domain.model.enum_type.TerminalStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.CondominioRepository;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.infra.service.TerminalPairingStore;
import com.jefiro.app247.infra.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pareamento ponta a ponta pela API; o Redis é trocado por um armazenamento em memória. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TerminalPairingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EmpresaRepository empresas;
    @Autowired CondominioRepository condominios;
    @Autowired TerminalRepository terminals;
    @Autowired UserRepository users;
    @Autowired TokenService tokens;
    @Autowired InMemoryPairingStore store;

    private Terminal terminal;
    private String gestor;
    private String gestorOutraEmpresa;
    private String serial;

    @TestConfiguration
    static class StoreConfig {
        @Bean @Primary
        InMemoryPairingStore inMemoryPairingStore() {
            return new InMemoryPairingStore();
        }
    }

    @BeforeEach
    void setUp() {
        Empresa empresa = empresa();
        terminal = terminal(condominio(empresa));
        gestor = "Bearer " + tokens.generateToken(user(empresa, RoleUser.GERENTE));
        gestorOutraEmpresa = "Bearer " + tokens.generateToken(user(empresa(), RoleUser.ADMIN));
        serial = "serial-" + UUID.randomUUID();
    }

    @Test
    void fluxoCompletoEntregaACredencialUmaUnicaVezEAtivaOTerminal() throws Exception {
        JsonNode start = start(serial);
        String token = start.get("pairingToken").asText();
        String code = start.get("code").asText();
        assertThat(code).matches("[2-9A-Z]{4}-[2-9A-Z]{4}");
        assertThat(start.get("qrPayload").asText()).isEqualTo("APP247-PAIR:" + code);

        claim(token).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.credential").doesNotExist());

        approve(terminal.getIdTerminal(), gestor, start.get("qrPayload").asText())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serialNumber").value(serial));

        JsonNode paired = json(claim(token).andExpect(status().isOk()));
        assertThat(paired.get("status").asText()).isEqualTo("PAIRED");
        assertThat(paired.get("terminalId").asText()).isEqualTo(terminal.getIdTerminal());
        assertThat(paired.get("activated").asBoolean()).isTrue();
        String credential = paired.get("credential").asText();
        assertThat(credential).startsWith("tdc_");

        mvc.perform(get("/terminal/{id}/bootstrap", terminal.getIdTerminal()).header("X-Terminal-Token", credential))
                .andExpect(status().isOk());
        claim(token).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("PAIRING_EXPIRED"));
        approve(terminal.getIdTerminal(), gestor, code).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAIRING_CODE_INVALID"));
    }

    @Test
    void codigoDigitadoSemHifenEMinusculoTambemVale() throws Exception {
        JsonNode start = start(serial);
        String digitado = start.get("code").asText().replace("-", "").toLowerCase();

        approve(terminal.getIdTerminal(), gestor, digitado).andExpect(status().isOk());
    }

    @Test
    void repararGeraCredencialNovaEInvalidaAAnterior() throws Exception {
        String primeira = parear(serial);
        String segunda = parear("serial-novo-" + UUID.randomUUID());

        mvc.perform(get("/terminal/{id}/bootstrap", terminal.getIdTerminal()).header("X-Terminal-Token", primeira))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/terminal/{id}/bootstrap", terminal.getIdTerminal()).header("X-Terminal-Token", segunda))
                .andExpect(status().isOk());
    }

    @Test
    void outraEmpresaNaoAprovaCodigoParaTerminalQueNaoEhDela() throws Exception {
        JsonNode start = start(serial);

        approve(terminal.getIdTerminal(), gestorOutraEmpresa, start.get("code").asText())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TERMINAL_NOT_FOUND"));
        claim(start.get("pairingToken").asText()).andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void codigoErradoNaoAprovaNada() throws Exception {
        approve(terminal.getIdTerminal(), gestor, "ZZZZ-ZZZZ").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAIRING_CODE_INVALID"));
        approve(terminal.getIdTerminal(), gestor, "curto").andExpect(status().isNotFound());
    }

    @Test
    void sessaoExpiradaRespondeGone() throws Exception {
        JsonNode start = start(serial);
        store.expireAll();

        claim(start.get("pairingToken").asText()).andExpect(status().isGone());
        approve(terminal.getIdTerminal(), gestor, start.get("code").asText()).andExpect(status().isNotFound());
    }

    @Test
    void serialDeOutroTerminalNaoEhRoubado() throws Exception {
        Terminal outro = terminal(terminal.getCondominio());
        outro.setSerialNumber(serial);
        terminals.saveAndFlush(outro);

        approve(terminal.getIdTerminal(), gestor, start(serial).get("code").asText())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TERMINAL_SERIAL_IN_USE"));
    }

    @Test
    void terminalDesativadoNaoPodeSerPareado() throws Exception {
        terminal.setAtivo(false);
        terminals.saveAndFlush(terminal);

        approve(terminal.getIdTerminal(), gestor, start(serial).get("code").asText())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TERMINAL_NOT_ACTIVE"));
    }

    @Test
    void rotasDeAprovacaoExigemGestorETokenInventadoNaoServe() throws Exception {
        mvc.perform(post("/terminais/{id}/pairing", terminal.getIdTerminal())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"AAAA-AAAA\"}"))
                .andExpect(status().isUnauthorized());
        claim("tpt_inventado").andExpect(status().isGone());
        mvc.perform(post("/terminal/pairing").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void variosTerminaisSemSerialPodemEsperarPareamento() throws Exception {
        String body = "{\"nome\":\"Aguardando\",\"serialNumber\":\"  \"}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/condominios/{id}/terminais", terminal.getCondominio().getIdCondominio())
                            .header("Authorization", gestor).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.serialNumber").value(org.hamcrest.Matchers.nullValue()));
        }
    }

    private String parear(String serialNumber) throws Exception {
        JsonNode start = start(serialNumber);
        approve(terminal.getIdTerminal(), gestor, start.get("code").asText()).andExpect(status().isOk());
        return json(claim(start.get("pairingToken").asText())).get("credential").asText();
    }

    private JsonNode start(String serialNumber) throws Exception {
        return json(mvc.perform(post("/terminal/pairing").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("serialNumber", serialNumber,
                                "macAddress", "aa:bb:cc:dd:ee:ff"))))
                .andExpect(status().isCreated()));
    }

    private ResultActions claim(String token) throws Exception {
        return mvc.perform(post("/terminal/pairing/claim").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("pairingToken", token))));
    }

    private ResultActions approve(String terminalId, String authorization, String code) throws Exception {
        return mvc.perform(post("/terminais/{id}/pairing", terminalId).header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("code", code))));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private Empresa empresa() {
        Empresa empresa = new Empresa();
        empresa.setRazaoSocial("Empresa Pareamento");
        empresa.setNomeFantasia("Empresa Pareamento");
        empresa.setCnpj(UUID.randomUUID().toString().substring(0, 18));
        empresa.setEmail(UUID.randomUUID() + "@example.test");
        empresa.setAtivo(true);
        return empresas.saveAndFlush(empresa);
    }

    private Condominio condominio(Empresa empresa) {
        Condominio condominio = new Condominio();
        condominio.setNome("Condominio Pareamento");
        condominio.setEmpresa(empresa);
        return condominios.saveAndFlush(condominio);
    }

    private Terminal terminal(Condominio condominio) {
        Terminal terminal = new Terminal();
        terminal.setCondominio(condominio);
        terminal.setNome("Terminal Pareamento");
        terminal.setCodigo("terminal-" + UUID.randomUUID());
        terminal.setAtivo(true);
        terminal.setStatus(TerminalStatus.OFFLINE);
        terminal.setLifecycleState(TerminalLifecycleState.ACTIVE);
        return terminals.saveAndFlush(terminal);
    }

    private User user(Empresa empresa, RoleUser role) {
        String cpf = String.format("%011d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000_000_000L));
        return users.saveAndFlush(User.builder().empresa(empresa).nome("Gestor")
                .email("pair-" + UUID.randomUUID() + "@example.test").senha("hash").cpf(cpf).ativo(true)
                .role(role).build());
    }

    static class InMemoryPairingStore implements TerminalPairingStore {
        private final Map<String, Session> sessions = new ConcurrentHashMap<>();
        private final Map<String, String> codes = new ConcurrentHashMap<>();

        @Override
        public boolean create(Session session, Duration ttl) {
            if (codes.putIfAbsent(session.code(), session.sessionId()) != null) return false;
            sessions.put(session.sessionId(), session);
            return true;
        }

        @Override
        public Optional<Session> find(String sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }

        @Override
        public Optional<String> sessionIdForCode(String code) {
            return Optional.ofNullable(codes.get(code));
        }

        @Override
        public boolean approve(Session session, String terminalId) {
            codes.remove(session.code());
            return sessions.computeIfPresent(session.sessionId(), (id, current) -> new Session(id, current.code(),
                    current.serialNumber(), current.macAddress(), current.ipAddress(), terminalId)) != null;
        }

        @Override
        public boolean consume(String sessionId) {
            return sessions.remove(sessionId) != null;
        }

        void expireAll() {
            sessions.clear();
            codes.clear();
        }
    }
}
