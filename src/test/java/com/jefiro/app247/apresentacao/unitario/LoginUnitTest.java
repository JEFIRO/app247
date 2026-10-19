package com.jefiro.app247.apresentacao.unitario;

import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.auth.AuthDTO;
import com.jefiro.app247.domain.model.dto.auth.AuthResponse;
import com.jefiro.app247.infra.exception.InvalidPasswordException;
import com.jefiro.app247.infra.service.CpfService;
import com.jefiro.app247.infra.service.TokenService;
import com.jefiro.app247.infra.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste unitário da tela de login: regra do {@link UserService} isolada com mocks
 * (sem banco, sem Spring). Cobre credenciais corretas, incorretas e o login restrito
 * ao administrador.
 */
// @ExtendWith(MockitoExtension.class): liga o Mockito ao JUnit 5. É ele que cria os objetos
// marcados com @Mock e @InjectMocks antes de cada teste e confere, ao final, se algum
// when(...) configurado ficou sem uso (stub desnecessário faz o teste falhar).
@ExtendWith(MockitoExtension.class)
class LoginUnitTest {

    private static final String CPF = "52998224725";

    // @Mock: cria um objeto FALSO (dublê) da classe. Ele não executa o código real nem toca
    // banco/rede; por padrão todo método devolve null, false, 0 ou vazio. O teste diz o que
    // ele deve responder com when(...).thenReturn(...) e depois confere com verify(...) se foi
    // chamado (e quantas vezes). Serve para testar UMA classe isolada das outras.
    //
    // @InjectMocks: cria um objeto REAL da classe testada e injeta nele os @Mock acima
    // (pelo construtor ou pelos campos). Assim o código do serviço roda de verdade, mas
    // tudo o que ele chama por fora é falso e controlado pelo teste.

    // Simula o Spring Security conferindo CPF e senha (aceita ou lança BadCredentialsException).
    @Mock AuthenticationManager authenticationManager;
    // Simula a geração do JWT; permite verificar que nenhum token sai em login recusado.
    @Mock TokenService tokenService;
    // Simula a validação/normalização do CPF informado no login.
    @Mock CpfService cpfService;
    // Classe testada de verdade: recebe os mocks acima no lugar das dependências reais.
    @InjectMocks UserService userService;

    @BeforeEach
    void cpfValido() {
        when(cpfService.requireValid(CPF)).thenReturn(CPF);
    }

    @Test
    @DisplayName("Login com credenciais corretas devolve token e dados do usuário")
    void loginComCredenciaisCorretas() {
        User user = usuario(RoleUser.USER);
        autenticacaoAceita(user);
        when(tokenService.generateToken(user)).thenReturn("jwt-valido");

        AuthResponse resposta = userService.login(new AuthDTO(CPF, "senha-correta"));

        assertThat(resposta.token()).isEqualTo("jwt-valido");
        assertThat(resposta.user().cpf()).isEqualTo(CPF);
        assertThat(resposta.user().nome()).isEqualTo("Maria");
    }

    @Test
    @DisplayName("Login com senha incorreta é recusado e não gera token")
    void loginComSenhaIncorreta() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> userService.login(new AuthDTO(CPF, "senha-errada")))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage("CPF ou senha inválidos");
        verify(tokenService, never()).generateToken(any());
    }

    @Test
    @DisplayName("Login administrativo aceita usuário ADMIN")
    void loginAdminAceitaAdministrador() {
        User admin = usuario(RoleUser.ADMIN);
        autenticacaoAceita(admin);
        when(tokenService.generateToken(admin)).thenReturn("jwt-admin");

        AuthResponse resposta = userService.loginAdmin(new AuthDTO(CPF, "senha-correta"));

        assertThat(resposta.token()).isEqualTo("jwt-admin");
    }

    @Test
    @DisplayName("Login administrativo recusa usuário comum com 403, mesmo com senha correta")
    void loginAdminRecusaUsuarioComum() {
        autenticacaoAceita(usuario(RoleUser.USER));

        assertThatThrownBy(() -> userService.loginAdmin(new AuthDTO(CPF, "senha-correta")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
        verify(tokenService, never()).generateToken(any());
    }

    private void autenticacaoAceita(User user) {
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private static User usuario(RoleUser role) {
        return User.builder().idUser("user-1").nome("Maria").sobrenome("Silva").cpf(CPF)
                .email("maria@example.com").senha("hash").ativo(true).role(role).build();
    }
}
