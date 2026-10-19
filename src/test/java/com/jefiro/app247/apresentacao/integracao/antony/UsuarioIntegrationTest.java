package com.jefiro.app247.apresentacao.integracao.antony;

import com.jefiro.app247.apresentacao.integracao.IntegracaoBase;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadastro ({@code POST /auth/register}) e remoção de usuários ({@code PUT /user/{id}}
 * com {@code ativo=false}). O identificador (ID) do usuário no cadastro é o CPF.
 */
class UsuarioIntegrationTest extends IntegracaoBase {

    @Test
    @DisplayName("Cadastro com nome completo, CPF, e-mail e telefone grava os dados normalizados")
    void cadastroValido() throws Exception {
        mvc.perform(cadastro("Maria da Silva", "529.982.247-25", "MARIA@EXAMPLE.COM", "(75) 99999-9999"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.id").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("maria@example.com"));

        User salvo = users.getByCpf("52998224725").orElseThrow();
        assertThat(salvo.getNome()).isEqualTo("Maria");
        assertThat(salvo.getSobrenome()).isEqualTo("da Silva");
        assertThat(salvo.getEmail()).isEqualTo("maria@example.com");
        assertThat(salvo.getTelefone()).isEqualTo("75999999999");
        assertThat(salvo.getRole()).isEqualTo(RoleUser.USER);
        assertThat(passwordEncoder.matches(SENHA, salvo.getSenha())).isTrue();
    }

    @Test
    @DisplayName("Validações: nome vazio, e-mail inválido, CPF inválido e telefone inválido retornam 400")
    void validacoesDeCadastro() throws Exception {
        mvc.perform(cadastro("", "52998224725", "a@example.com", null))
                .andExpect(status().isBadRequest());
        mvc.perform(cadastro("Ana Souza", "52998224725", "email-invalido", null))
                .andExpect(status().isBadRequest());
        mvc.perform(cadastro("Ana Souza", "11111111111", "ana@example.com", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CPF"));
        mvc.perform(cadastro("Ana Souza", "52998224725", "ana@example.com", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REGISTRATION_DATA"));

        assertThat(users.existsByCpf("52998224725")).isFalse();
    }

    @Test
    @DisplayName("CPF ou e-mail já cadastrados retornam 409")
    void cadastroDuplicado() throws Exception {
        mvc.perform(cadastro("Ana Souza", "11144477735", "ana@example.com", null)).andExpect(status().isCreated());

        mvc.perform(cadastro("Outra Pessoa", "111.444.777-35", "outra@example.com", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CPF_ALREADY_REGISTERED"));
        mvc.perform(cadastro("Outra Pessoa", "16899535009", "ANA@example.com", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("Administrador remove (desativa) usuário e ele não consegue mais fazer login")
    void administradorRemoveUsuario() throws Exception {
        Empresa empresa = novaEmpresa();
        User admin = novoUsuario(empresa, "39053344705", RoleUser.ADMIN);
        User alvo = novoUsuario(empresa, "52998224725", RoleUser.USER);
        mvc.perform(loginDe(alvo)).andExpect(status().isOk());

        mvc.perform(put("/user/{id}", alvo.getIdUser()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isOk());

        assertThat(users.findById(alvo.getIdUser()).orElseThrow().getAtivo()).isFalse();
        mvc.perform(loginDe(alvo)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Usuário comum não consegue remover outro usuário")
    void usuarioComumNaoRemove() throws Exception {
        Empresa empresa = novaEmpresa();
        User comum = novoUsuario(empresa, "11144477735", RoleUser.USER);
        User alvo = novoUsuario(empresa, "52998224725", RoleUser.USER);

        mvc.perform(put("/user/{id}", alvo.getIdUser()).header("Authorization", bearer(comum))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isNotFound());

        assertThat(users.findById(alvo.getIdUser()).orElseThrow().getAtivo()).isTrue();
    }

    private static org.springframework.test.web.servlet.RequestBuilder cadastro(
            String nome, String cpf, String email, String telefone) {
        String body = "{\"nome\":\"" + nome + "\",\"cpf\":\"" + cpf + "\",\"email\":\"" + email
                + "\",\"telefone\":" + (telefone == null ? "null" : "\"" + telefone + "\"")
                + ",\"senha\":\"" + SENHA + "\"}";
        return post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static org.springframework.test.web.servlet.RequestBuilder loginDe(User user) {
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cpf\":\"" + user.getCpf() + "\",\"senha\":\"" + SENHA + "\"}");
    }
}
