package com.jefiro.app247.apresentacao.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.event.listener.UserListener;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.infra.service.RegistrationRateLimiter;
import com.jefiro.app247.infra.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Base dos testes de integração da apresentação.
 *
 * <p>Sobe a aplicação inteira com o perfil {@code test} (H2 em memória, Flyway e workers
 * desligados) e chama os endpoints reais via MockMvc, passando pelos filtros de segurança
 * JWT. Cada teste roda em uma transação desfeita ao final ({@code @Transactional}), então
 * nenhum dado persiste e nenhum banco real é tocado.
 */
// @SpringBootTest: sobe o contexto Spring completo (controllers, services, repositórios,
// segurança), igual à aplicação rodando, só que dentro do teste. As properties desligam
// tarefas agendadas e a reconciliação de pagamentos, que não interessam aqui.
// @AutoConfigureMockMvc: cria o MockMvc, que simula requisições HTTP sem abrir porta.
// @ActiveProfiles("test"): carrega application-test.properties (H2 em memória).
// @Transactional: cada teste roda numa transação desfeita no final; o banco volta limpo.
@SpringBootTest(properties = {"spring.task.scheduling.enabled=false",
        "payment.reconciliation.startup.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class IntegracaoBase {

    protected static final String SENHA = "senha123";
    protected final ObjectMapper json = new ObjectMapper();

    // @Autowired: pede ao Spring o bean REAL já criado no contexto (nada é falso aqui).
    // Dispara requisições HTTP simuladas contra os controllers reais (com filtros de segurança).
    @Autowired protected MockMvc mvc;
    // Repositório JPA de usuários: cria os usuários do cenário e confere o que foi gravado.
    @Autowired protected UserRepository users;
    // Repositório JPA de empresas: cria a empresa (tenant) de cada cenário.
    @Autowired protected EmpresaRepository empresas;
    // Codificador BCrypt real: grava senhas criptografadas como no cadastro de verdade.
    @Autowired protected PasswordEncoder passwordEncoder;
    // Gera o JWT usado no header Authorization das rotas protegidas.
    @Autowired protected TokenService tokenService;

    // @MockitoBean: troca, DENTRO do contexto Spring, o bean real por um mock do Mockito.
    // Todo lugar da aplicação que usa essa classe passa a receber o objeto falso. Usado para
    // o que depende de coisa externa e não deve rodar no teste: limite de tentativas de
    // cadastro (Redis) e listener de boas-vindas (envio de e-mail). Diferença para @Mock:
    // @Mock só existe no teste unitário; @MockitoBean substitui o bean na aplicação inteira.
    @MockitoBean protected RegistrationRateLimiter registrationRateLimiter;
    @MockitoBean protected UserListener userListener;

    // Por padrão o mock devolveria false (bloquearia todo cadastro); aqui ele sempre libera.
    @BeforeEach
    void liberarRateLimit() {
        when(registrationRateLimiter.allow(any(), any(), any())).thenReturn(true);
    }

    protected Empresa novaEmpresa() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        return empresas.saveAndFlush(Empresa.builder().razaoSocial("Mercadinho " + sufixo)
                .nomeFantasia("Mercadinho " + sufixo).cnpj("CNPJ-" + sufixo)
                .email(sufixo + "@empresa.test").ativo(true).build());
    }

    /** Cria um usuário com a senha {@link #SENHA} criptografada (BCrypt), como no cadastro real. */
    protected User novoUsuario(Empresa empresa, String cpf, RoleUser role) {
        return users.saveAndFlush(User.builder().empresa(empresa).nome("Pessoa").sobrenome("Teste")
                .email(UUID.randomUUID() + "@example.test").senha(passwordEncoder.encode(SENHA))
                .cpf(cpf).ativo(true).role(role).build());
    }

    protected String bearer(User user) {
        return "Bearer " + tokenService.generateToken(user);
    }

    protected JsonNode corpo(org.springframework.test.web.servlet.ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }
}
