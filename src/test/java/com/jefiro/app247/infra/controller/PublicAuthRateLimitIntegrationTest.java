package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.exception.InvalidCodeException;
import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.infra.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Limites das rotas públicas e 401 para JWT inválido. IP, CPF e e-mail são aleatórios por
 * execução porque os contadores podem cair num Redis real que sobrevive entre execuções.
 */
@SpringBootTest(properties = {
        "app.security.login.max-failures-per-cpf=3",
        "app.security.login.max-attempts-per-origin=6",
        "app.security.email-code.send-per-email=2",
        "app.security.email-code.send-per-origin=100",
        "app.security.email-code.validate-per-email=3",
        "app.security.onboarding.per-origin=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicAuthRateLimitIntegrationTest {
    private static final String SENHA = "senha-correta-123";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoSpyBean UserService userService;

    private String ip;

    @BeforeEach
    void setup() {
        ip = randomIp();
        doNothing().when(userService).sendCode(any());
        doNothing().when(userService).verificarCode(any());
        doNothing().when(userService).invalidarCode(anyString());
    }

    @Test
    void cpfComFalhasDemaisFicaBloqueadoMesmoComSenhaCorreta() throws Exception {
        User user = user();
        for (int i = 0; i < 3; i++) login(user.getCpf(), "errada", ip).andExpect(status().isUnauthorized());

        login(user.getCpf(), SENHA, randomIp())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
        mvc.perform(post("/auth/login/admin").with(from(randomIp())).contentType(MediaType.APPLICATION_JSON)
                        .content(body(user.getCpf(), SENHA)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void loginBemSucedidoZeraAsFalhas() throws Exception {
        User user = user();
        login(user.getCpf(), "errada", randomIp()).andExpect(status().isUnauthorized());
        login(user.getCpf(), "errada", randomIp()).andExpect(status().isUnauthorized());
        login(user.getCpf(), SENHA, randomIp()).andExpect(status().isOk());

        login(user.getCpf(), "errada", randomIp()).andExpect(status().isUnauthorized());
        login(user.getCpf(), "errada", randomIp()).andExpect(status().isUnauthorized());
        login(user.getCpf(), SENHA, randomIp()).andExpect(status().isOk());
    }

    @Test
    void origemComTentativasDemaisFicaBloqueada() throws Exception {
        for (int i = 0; i < 6; i++) login(randomCpf(), "qualquer", ip).andExpect(status().isUnauthorized());

        login(randomCpf(), "qualquer", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
    }

    @Test
    void envioDeCodigoLimitadoPorEmail() throws Exception {
        String email = "rate-" + UUID.randomUUID() + "@example.test";
        sendCode(email, randomIp()).andExpect(status().isOk());
        sendCode(email.toUpperCase(), randomIp()).andExpect(status().isOk());

        sendCode(email, randomIp())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_RATE_LIMITED"));
    }

    @Test
    void validacaoDeCodigoEsgotadaInvalidaOCodigo() throws Exception {
        String email = "rate-" + UUID.randomUUID() + "@example.test";
        doThrow(new InvalidCodeException("Código inválido")).when(userService).verificarCode(any());
        for (int i = 0; i < 3; i++) validateCode(email).andExpect(status().is4xxClientError());

        validateCode(email)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_RATE_LIMITED"));
        verify(userService).invalidarCode(email);
    }

    @Test
    void validacaoCorretaZeraAsTentativas() throws Exception {
        String email = "rate-" + UUID.randomUUID() + "@example.test";
        for (int i = 0; i < 5; i++) validateCode(email).andExpect(status().isOk());
        verify(userService, never()).invalidarCode(anyString());
    }

    @Test
    void onboardingLimitadoPorOrigem() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/onboarding").with(from(ip)).contentType(MediaType.APPLICATION_JSON).content(onboarding()))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/onboarding").with(from(ip)).contentType(MediaType.APPLICATION_JSON).content(onboarding()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ONBOARDING_RATE_LIMITED"));
    }

    @Test
    void jwtInvalidoRetorna401ComCodigoEmRotaProtegida() throws Exception {
        mvc.perform(get("/user/qualquer").header("Authorization", "Bearer token.invalido.aqui"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void jwtInvalidoNaoQuebraRotaPublica() throws Exception {
        User user = user();
        mvc.perform(post("/auth/login").with(from(randomIp())).header("Authorization", "Bearer lixo")
                        .contentType(MediaType.APPLICATION_JSON).content(body(user.getCpf(), SENHA)))
                .andExpect(status().isOk());
        verify(userService, never()).invalidarCode(anyString());
    }

    private ResultActions login(String cpf, String senha, String origin) throws Exception {
        return mvc.perform(post("/auth/login").with(from(origin)).contentType(MediaType.APPLICATION_JSON)
                .content(body(cpf, senha)));
    }

    private ResultActions sendCode(String email, String origin) throws Exception {
        return mvc.perform(post("/auth/send-code").with(from(origin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"nome\":\"Ana\"}"));
    }

    private ResultActions validateCode(String email) throws Exception {
        return mvc.perform(post("/auth/validate-code").with(from(randomIp())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"123456\"}"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor from(String origin) {
        return request -> {
            request.setRemoteAddr(origin);
            return request;
        };
    }

    private static String onboarding() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        String cnpj = String.format("%014d", ThreadLocalRandom.current().nextLong(1L, 99_999_999_999_999L));
        return """
                {"gestor":{"nome":"Gestor","sobrenome":"Rate","email":"gestor-%s@example.test","senha":"senha-123",
                           "cpf":"%s","telefone":"75999999999"},
                 "empresa":{"razaoSocial":"Empresa %s","nomeFantasia":"Empresa %s","cnpj":"%s",
                            "email":"empresa-%s@example.test","telefone":"7533334444","cep":"44000000",
                            "logradouro":"Rua A","numero":"1","bairro":"Centro","cidade":"Feira","estado":"BA"},
                 "condominio":{"nome":"Condominio %s"},
                 "terminal":{"nome":"Terminal %s","serialNumber":"SERIAL-%s"}}
                """.formatted(id, randomCpf(), id, id, cnpj, id, id, id, id);
    }

    private static String body(String cpf, String senha) {
        return "{\"cpf\":\"" + cpf + "\",\"senha\":\"" + senha + "\"}";
    }

    private User user() {
        return users.saveAndFlush(User.builder().nome("Pessoa").email("rate-" + UUID.randomUUID() + "@example.test")
                .senha(passwordEncoder.encode(SENHA)).cpf(randomCpf()).ativo(true).role(RoleUser.USER).build());
    }

    private static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(1, 255);
    }

    /** CPF com dígitos verificadores válidos, para passar por CpfService.requireValid. */
    private static String randomCpf() {
        int[] d = new int[11];
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 9; i++) d[i] = r.nextInt(10);
        d[9] = digit(d, 9);
        d[10] = digit(d, 10);
        StringBuilder cpf = new StringBuilder();
        for (int v : d) cpf.append(v);
        return cpf.toString();
    }

    private static int digit(int[] d, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) sum += d[i] * (length + 1 - i);
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }
}
