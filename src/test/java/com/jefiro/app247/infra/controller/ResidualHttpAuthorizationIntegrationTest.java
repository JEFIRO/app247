package com.jefiro.app247.infra.controller;

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
import com.jefiro.app247.infra.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ResidualHttpAuthorizationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EmpresaRepository empresas;
    @Autowired UserRepository users;
    @Autowired TokenService tokens;
    @Autowired CondominioRepository condominios;
    @Autowired TerminalRepository terminals;
    private User userA;
    private User userA2;
    private User adminA;
    private User userB;

    @BeforeEach
    void setup() {
        Empresa a = empresa("A");
        Empresa b = empresa("B");
        userA = user(a, "61000001130", RoleUser.USER);
        userA2 = user(a, "61000002292", RoleUser.USER);
        adminA = user(a, "61000003345", RoleUser.ADMIN);
        userB = user(b, "61000004406", RoleUser.USER);
    }

    @Test
    void userExigeJwtEOcultaOutroUsuario() throws Exception {
        mvc.perform(get("/user/{id}", userA.getIdUser())).andExpect(status().isUnauthorized());
        mvc.perform(get("/user/{id}", userA.getIdUser()).header("Authorization", bearer(userA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(userA.getIdUser()));
        mvc.perform(get("/user/{id}", userA2.getIdUser()).header("Authorization", bearer(userA)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/user/{id}", userB.getIdUser()).header("Authorization", bearer(userA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminAcessaSomenteUsuariosDoProprioTenant() throws Exception {
        mvc.perform(get("/user/{id}", userA2.getIdUser()).header("Authorization", bearer(adminA)))
                .andExpect(status().isOk());
        mvc.perform(get("/user/{id}", userB.getIdUser()).header("Authorization", bearer(adminA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listagemDeOrdersNaoConfiaEmUserIdAlheio() throws Exception {
        mvc.perform(get("/user/{id}/orders", userA.getIdUser()).header("Authorization", bearer(userA)))
                .andExpect(status().isOk());
        mvc.perform(get("/user/{id}/orders", userA2.getIdUser()).header("Authorization", bearer(userA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rotasTerminalCheckoutEComprovanteExigemDevicePrincipal() throws Exception {
        mvc.perform(get("/carrinho/qualquer")).andExpect(status().isUnauthorized());
        mvc.perform(get("/order").param("carrinho_id", "qualquer"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/checkout/carrinho").param("idCarrinho", "qualquer"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/comprovante").contentType(MediaType.APPLICATION_JSON).content("""
                {"terminalId":"a","pedidoId":"b","tipoEnvio":"EMAIL","destinatario":"a@example.com"}
                """)).andExpect(status().isUnauthorized());
        mvc.perform(get("/comprovante/qualquer").param("terminalId", "a"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkoutMobileLegadoPermaneceBloqueadoMesmoComJwt() throws Exception {
        mvc.perform(get("/checkout").param("id", userB.getIdUser()).param("session", "session")
                        .header("Authorization", bearer(userA)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/checkout/session").param("idSession", "session")
                        .header("Authorization", bearer(userA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void fallbackNegaRotaNaoClassificadaEOperacoesInternas() throws Exception {
        mvc.perform(get("/rota-nao-classificada")).andExpect(status().isUnauthorized());
        mvc.perform(post("/files/upload")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/testes/mercadopago/listar"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void consultaPublicaPorSerialExpoeSomenteDadosDeAtivacao() throws Exception {
        Empresa empresa = empresa("Serial");
        empresa.setAtivo(true);
        Condominio condominio = new Condominio();
        condominio.setNome("Condominio Serial");
        condominio.setEmpresa(empresa);
        condominio.setAtivo(true);
        condominio = condominios.saveAndFlush(condominio);
        Terminal terminal = new Terminal();
        terminal.setCondominio(condominio);
        terminal.setNome("Terminal Serial");
        terminal.setCodigo("terminal-serial-" + UUID.randomUUID());
        terminal.setSerialNumber("SERIAL-" + UUID.randomUUID());
        terminal.setAtivo(true);
        terminal.setStatus(TerminalStatus.OFFLINE);
        terminal.setLifecycleState(TerminalLifecycleState.ACTIVE);
        terminal = terminals.saveAndFlush(terminal);

        mvc.perform(get("/terminal/serial/{serial}", terminal.getSerialNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminalId").value(terminal.getIdTerminal()))
                .andExpect(jsonPath("$.serialNumber").value(terminal.getSerialNumber()))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.activated").value(true))
                .andExpect(jsonPath("$.nome").doesNotExist())
                .andExpect(jsonPath("$.codigo").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.condominioId").doesNotExist())
                .andExpect(jsonPath("$.condominioNome").doesNotExist())
                .andExpect(jsonPath("$.lifecycleState").doesNotExist());
    }

    @Test
    void migracaoComTokenGlobalLegadoFoiRemovida() throws Exception {
        mvc.perform(post("/terminal/{id}/credential/migrate", "qualquer")
                        .header("X-Terminal-Token", "test-only-terminal-legacy-secret"))
                .andExpect(status().isUnauthorized());
    }

    private String bearer(User user) { return "Bearer " + tokens.generateToken(user); }

    private Empresa empresa(String suffix) {
        Empresa e = new Empresa();
        e.setRazaoSocial("Empresa " + suffix);
        e.setNomeFantasia("Empresa " + suffix);
        e.setCnpj(UUID.randomUUID().toString().substring(0, 18));
        e.setEmail(UUID.randomUUID() + "@example.test");
        return empresas.saveAndFlush(e);
    }

    private User user(Empresa empresa, String cpf, RoleUser role) {
        User u = User.builder().empresa(empresa).nome("Pessoa").email(UUID.randomUUID() + "@example.test")
                .senha("hash").cpf(cpf).ativo(true).role(role).build();
        return users.saveAndFlush(u);
    }
}
