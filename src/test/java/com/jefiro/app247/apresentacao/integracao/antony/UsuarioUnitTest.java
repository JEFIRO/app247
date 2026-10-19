package com.jefiro.app247.apresentacao.integracao.antony;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.UserRequestDTO;
import com.jefiro.app247.domain.model.dto.UserUpdate;
import com.jefiro.app247.infra.exception.DuplicateCpfException;
import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.infra.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
@ExtendWith(MockitoExtension.class)
class UsuarioUnitTest {

    @Mock UserRepository repository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ApplicationEventPublisher publisher;
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


    // Testes da entidade User (não usam os mocks do serviço)


    private static List<String> authorityNames(User user) {
        return user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private static User userComRole(RoleUser role) {
        return User.builder().role(role).build();
    }

    @Nested
    @DisplayName("Entidade User: Builder")
    class BuilderTests {

        @Test
        @DisplayName("aplica os valores padrão: ativo=true, emailVerificado=false, role=USER")
        void builderAplicaDefaults() {
            User user = User.builder().nome("Ana").email("ana@email.com").build();

            assertThat(user.getAtivo()).isTrue();
            assertThat(user.getEmailVerificado()).isFalse();
            assertThat(user.getRole()).isEqualTo(RoleUser.USER);
        }

        @Test
        @DisplayName("permite sobrescrever os valores padrão")
        void builderPermiteSobrescreverDefaults() {
            User user = User.builder()
                    .ativo(false)
                    .emailVerificado(true)
                    .role(RoleUser.ADMIN)
                    .build();

            assertThat(user.getAtivo()).isFalse();
            assertThat(user.getEmailVerificado()).isTrue();
            assertThat(user.getRole()).isEqualTo(RoleUser.ADMIN);
        }
    }

    @Nested
    @DisplayName("Entidade User: construtor a partir do UserRequestDTO")
    class ConstrutorDtoTests {

        // nome, sobrenome, email, senha, cpf, telefone, dataNascimento, roleUser
        private UserRequestDTO dto(RoleUser role) {
            return new UserRequestDTO("Maria", "Silva", "maria@email.com", "senha-hash",
                    "12345678901", "75999999999", LocalDate.of(1995, 5, 20), role);
        }

        @Test
        @DisplayName("copia os campos do DTO")
        void copiaCamposDoDto() {
            User user = new User(dto(RoleUser.GERENTE));

            assertThat(user.getNome()).isEqualTo("Maria");
            assertThat(user.getSobrenome()).isEqualTo("Silva");
            assertThat(user.getEmail()).isEqualTo("maria@email.com");
            assertThat(user.getSenha()).isEqualTo("senha-hash");
            assertThat(user.getCpf()).isEqualTo("12345678901");
            assertThat(user.getTelefone()).isEqualTo("75999999999");
            assertThat(user.getDataNascimento()).isEqualTo(LocalDate.of(1995, 5, 20));
            assertThat(user.getRole()).isEqualTo(RoleUser.GERENTE);
        }

        @Test
        @DisplayName("novo usuário nasce ativo e com e-mail não verificado")
        void iniciaAtivoESemEmailVerificado() {
            User user = new User(dto(RoleUser.USER));

            assertThat(user.getAtivo()).isTrue();
            assertThat(user.getEmailVerificado()).isFalse();
        }

        @Test
        @DisplayName("usa role USER quando o DTO não informa role")
        void roleNulaViraUser() {
            User user = new User(dto(null));

            assertThat(user.getRole()).isEqualTo(RoleUser.USER);
        }
    }

    @Nested
    @DisplayName("Entidade User: callbacks JPA")
    class CallbacksTests {


        @Test
        @DisplayName("prePersist define createdAt e updatedAt")
        void prePersistDefineDatas() {
            User user = new User();
            Instant antes = Instant.now();

            ReflectionTestUtils.invokeMethod(user, "prePersist");

            assertThat(user.getCreatedAt()).isNotNull().isBetween(antes, Instant.now());
            assertThat(user.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("prePersist não sobrescreve createdAt já existente")
        void prePersistPreservaCreatedAt() {
            User user = new User();
            Instant original = Instant.parse("2024-01-01T00:00:00Z");
            user.setCreatedAt(original);

            ReflectionTestUtils.invokeMethod(user, "prePersist");

            assertThat(user.getCreatedAt()).isEqualTo(original);
            assertThat(user.getUpdatedAt()).isAfter(original);
        }

        @Test
        @DisplayName("prePersist preenche defaults nulos (ativo, emailVerificado, role)")
        void prePersistPreencheDefaultsNulos() {
            User user = new User(); // NoArgsConstructor não aplica @Builder.Default
            user.setAtivo(null);
            user.setEmailVerificado(null);
            user.setRole(null);

            ReflectionTestUtils.invokeMethod(user, "prePersist");

            assertThat(user.getAtivo()).isTrue();
            assertThat(user.getEmailVerificado()).isFalse();
            assertThat(user.getRole()).isEqualTo(RoleUser.USER);
        }

        @Test
        @DisplayName("prePersist não altera defaults já definidos")
        void prePersistNaoAlteraValoresDefinidos() {
            User user = User.builder()
                    .ativo(false)
                    .emailVerificado(true)
                    .role(RoleUser.ADMIN)
                    .build();

            ReflectionTestUtils.invokeMethod(user, "prePersist");

            assertThat(user.getAtivo()).isFalse();
            assertThat(user.getEmailVerificado()).isTrue();
            assertThat(user.getRole()).isEqualTo(RoleUser.ADMIN);
        }

        @Test
        @DisplayName("preUpdate atualiza updatedAt sem mexer em createdAt")
        void preUpdateAtualizaUpdatedAt() {
            User user = new User();
            Instant passado = Instant.parse("2024-01-01T00:00:00Z");
            user.setCreatedAt(passado);
            user.setUpdatedAt(passado);

            ReflectionTestUtils.invokeMethod(user, "preUpdate");

            assertThat(user.getUpdatedAt()).isAfter(passado);
            assertThat(user.getCreatedAt()).isEqualTo(passado);
        }
    }

    @Nested
    @DisplayName("Entidade User: UserDetails")
    class UserDetailsTests {

        @Test
        @DisplayName("ADMIN possui ROLE_ADMIN, ROLE_GERENTE e ROLE_USER")
        void authoritiesAdmin() {
            assertThat(authorityNames(userComRole(RoleUser.ADMIN)))
                    .containsExactly("ROLE_ADMIN", "ROLE_GERENTE", "ROLE_USER");
        }

        @Test
        @DisplayName("GERENTE possui ROLE_GERENTE e ROLE_USER")
        void authoritiesGerente() {
            assertThat(authorityNames(userComRole(RoleUser.GERENTE)))
                    .containsExactly("ROLE_GERENTE", "ROLE_USER");
        }

        @Test
        @DisplayName("USER possui apenas ROLE_USER")
        void authoritiesUser() {
            assertThat(authorityNames(userComRole(RoleUser.USER)))
                    .containsExactly("ROLE_USER");
        }

        @Test
        @DisplayName("getUsername retorna o CPF")
        void usernameEhCpf() {
            User user = User.builder().cpf("12345678901").email("a@a.com").build();

            assertThat(user.getUsername()).isEqualTo("12345678901");
        }

        @Test
        @DisplayName("getPassword retorna a senha")
        void passwordEhSenha() {
            User user = User.builder().senha("hash-da-senha").build();

            assertThat(user.getPassword()).isEqualTo("hash-da-senha");
        }

        @Test
        @DisplayName("isEnabled reflete o campo ativo")
        void isEnabledReflitaAtivo() {
            assertThat(User.builder().ativo(true).build().isEnabled()).isTrue();
            assertThat(User.builder().ativo(false).build().isEnabled()).isFalse();
        }

        @Test
        @DisplayName("isEnabled é false quando ativo é nulo")
        void isEnabledFalseQuandoAtivoNulo() {
            User user = new User();
            user.setAtivo(null);

            assertThat(user.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("flags padrão do UserDetails estão liberadas")
        void flagsPadraoDoUserDetails() {
            User user = User.builder().build();

            assertThat(user.isAccountNonExpired()).isTrue();
            assertThat(user.isAccountNonLocked()).isTrue();
            assertThat(user.isCredentialsNonExpired()).isTrue();
        }
    }
}