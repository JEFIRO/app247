package com.jefiro.app247.apresentacao.unitario;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.UserRequestDTO;
import com.jefiro.app247.domain.model.dto.UserUpdate;
import com.jefiro.app247.infra.exception.DuplicateCpfException;
import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.infra.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste unitário de cadastro e remoção (desativação) de usuários no {@link UserService}.
 * O sistema não apaga usuários fisicamente: "remover" é desativar ({@code ativo=false}),
 * o que preserva o histórico de compras e impede o login.
 */
// @ExtendWith(MockitoExtension.class): liga o Mockito ao JUnit 5. É ele que cria os objetos
// marcados com @Mock e @InjectMocks antes de cada teste e confere, ao final, se algum
// when(...) configurado ficou sem uso (stub desnecessário faz o teste falhar).
@ExtendWith(MockitoExtension.class)
class UsuarioUnitTest {

    // @Mock: cria um objeto FALSO (dublê) da classe. Ele não executa o código real nem toca
    // banco/rede; por padrão todo método devolve null, false, 0 ou vazio. O teste diz o que
    // ele deve responder com when(...).thenReturn(...) e depois confere com verify(...) se foi
    // chamado (e quantas vezes). Serve para testar UMA classe isolada das outras.
    //
    // @InjectMocks: cria um objeto REAL da classe testada e injeta nele os @Mock acima
    // (pelo construtor ou pelos campos). Assim o código do serviço roda de verdade, mas
    // tudo o que ele chama por fora é falso e controlado pelo teste.

    // Simula o banco de usuários: checagem de CPF duplicado e o save.
    @Mock UserRepository repository;
    // Simula a criptografia da senha (devolve um hash fixo para conferir).
    @Mock PasswordEncoder passwordEncoder;
    // Simula o publicador do evento de usuário criado (que dispara o e-mail de boas-vindas).
    @Mock ApplicationEventPublisher publisher;
    // Classe testada de verdade: recebe os mocks acima no lugar das dependências reais.
    @InjectMocks UserService userService;

    @Test
    @DisplayName("Cadastro de gestor grava senha criptografada e papel ADMIN")
    void cadastroDeGestor() {
        when(repository.existsByCpf("52998224725")).thenReturn(false);
        when(passwordEncoder.encode("segredo")).thenReturn("hash-bcrypt");
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        Empresa empresa = Empresa.builder().id("empresa-a").build();

        User salvo = userService.cadastrarGestor(request("52998224725"), empresa);

        assertThat(salvo.getRole()).isEqualTo(RoleUser.ADMIN);
        assertThat(salvo.getSenha()).isEqualTo("hash-bcrypt");
        assertThat(salvo.getEmpresa()).isSameAs(empresa);
        assertThat(salvo.getEmail()).isEqualTo("joao@example.com");
        assertThat(salvo.getTelefone()).isEqualTo("75999999999");
    }

    @Test
    @DisplayName("Cadastro com CPF já existente é recusado e nada é salvo")
    void cadastroComCpfDuplicado() {
        when(repository.existsByCpf("52998224725")).thenReturn(true);

        assertThatThrownBy(() -> userService.cadastrarGestor(request("52998224725"), new Empresa()))
                .isInstanceOf(DuplicateCpfException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Administrador remove (desativa) usuário; usuário desativado não pode mais logar")
    void administradorDesativaUsuario() {
        User admin = usuario(RoleUser.ADMIN);
        User alvo = usuario(RoleUser.USER);

        userService.atualizarUsuario(admin, alvo, new UserUpdate(null, null, null, null, false, null));

        assertThat(alvo.getAtivo()).isFalse();
        assertThat(alvo.isEnabled()).isFalse();
        verify(repository).save(alvo);
    }

    @Test
    @DisplayName("Usuário comum não consegue desativar uma conta")
    void usuarioComumNaoDesativa() {
        User comum = usuario(RoleUser.USER);

        userService.atualizarUsuario(comum, comum, new UserUpdate("Novo", null, null, null, false, null));

        assertThat(comum.getNome()).isEqualTo("Novo");
        assertThat(comum.getAtivo()).isTrue();
    }

    private static UserRequestDTO request(String cpf) {
        return new UserRequestDTO("João", "Souza", "joao@example.com", "segredo", cpf,
                "75999999999", null, null);
    }

    private static User usuario(RoleUser role) {
        return User.builder().nome("Pessoa").cpf("11144477735").email("p@example.com")
                .senha("hash").ativo(true).role(role).build();
    }
}
