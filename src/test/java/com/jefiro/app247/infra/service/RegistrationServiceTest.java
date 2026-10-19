package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.auth.ClientRegistrationRequest;
import com.jefiro.app247.domain.model.dto.auth.ClientRegistrationResponse;
import com.jefiro.app247.infra.event.UserCreatedEvent;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final String CPF_VALIDO = "52998224725";
    private static final String TERMOS_V = "2026-09";
    private static final String PRIVACIDADE_V = "2026-09";

    @Mock
    private UserRepository users;
    @Mock
    private PasswordEncoder passwords;
    @Mock
    private ApplicationEventPublisher publisher;

    private RegistrationService service;

    @BeforeEach
    void setUp() {
        service = serviceCom(legalNaoConfigurado());
    }



    @Test
    void cadastraClienteComDadosNormalizados() {
        persistindoComId("user-1");

        ClientRegistrationResponse response = service.register(request(
                "Jefferson", "Vitena", "529.982.247-25", "  Jefferson@Example.COM ", "(75) 99999-9999"));

        User salvo = usuarioSalvo();
        assertThat(salvo.getCpf()).isEqualTo(CPF_VALIDO);
        assertThat(salvo.getEmail()).isEqualTo("jefferson@example.com");
        assertThat(salvo.getTelefone()).isEqualTo("75999999999");
        assertThat(salvo.getNome()).isEqualTo("Jefferson");
        assertThat(salvo.getSobrenome()).isEqualTo("Vitena");
        assertThat(salvo.getSenha()).isEqualTo("hash-da-senha");
        assertThat(salvo.getRole()).isEqualTo(RoleUser.USER);
        assertThat(salvo.getAtivo()).isTrue();
        assertThat(salvo.getEmailVerificado()).isFalse();
        assertThat(salvo.getCpfVerifiedAt()).isNull();
        assertThat(salvo.getEmpresa()).isNull();
        assertThat(salvo.getCondominio()).isNull();

        assertThat(response.user().id()).isEqualTo("user-1");
        assertThat(response.user().nome()).isEqualTo("Jefferson Vitena");
        assertThat(response.user().email()).isEqualTo("jefferson@example.com");
        assertThat(response.cpfVerificationStatus()).isEqualTo("UNVERIFIED");
        assertThat(response.condominio()).isNull();
        assertThat(response.requiresLogin()).isTrue();
        assertThat(response.legalDocumentsConfigured()).isFalse();

        verify(passwords).encode("SenhaSegura123");
    }

    @Test
    void publicaUserCreatedEventComUsuarioPersistido() {
        persistindoComId("user-1");

        service.register(requestValida());

        ArgumentCaptor<UserCreatedEvent> evento = ArgumentCaptor.forClass(UserCreatedEvent.class);
        verify(publisher).publishEvent(evento.capture());
        assertThat(evento.getValue().user().getIdUser()).isEqualTo("user-1");
    }

    // ---------- nome ----------

    @Test
    void semSobrenomeDivideNomeCompletoNoPrimeiroEspaco() {
        persistindoComId("user-1");

        ClientRegistrationResponse response = service.register(
                request("  Jefferson   Henrique  Vitena ", null, CPF_VALIDO, "a@b.com", null));

        User salvo = usuarioSalvo();
        assertThat(salvo.getNome()).isEqualTo("Jefferson");
        assertThat(salvo.getSobrenome()).isEqualTo("Henrique Vitena");
        assertThat(response.user().nome()).isEqualTo("Jefferson Henrique Vitena");
    }

    @Test
    void nomeSemEspacoESemSobrenomeFicaSemSobrenome() {
        persistindoComId("user-1");

        ClientRegistrationResponse response = service.register(
                request("Jefferson", "   ", CPF_VALIDO, "a@b.com", null));

        assertThat(usuarioSalvo().getSobrenome()).isNull();
        assertThat(response.user().nome()).isEqualTo("Jefferson");
    }

    @Test
    void sobrenomeExplicitoMantemNomeInteiro() {
        persistindoComId("user-1");

        service.register(request("Jefferson  Henrique", " Vitena ", CPF_VALIDO, "a@b.com", null));

        User salvo = usuarioSalvo();
        assertThat(salvo.getNome()).isEqualTo("Jefferson Henrique");
        assertThat(salvo.getSobrenome()).isEqualTo("Vitena");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void nomeAusenteERejeitadoSemConsultarBanco(String nome) {
        assertInvalidData(() -> service.register(request(nome, "Vitena", CPF_VALIDO, "a@b.com", null)));
        verifyNoInteractions(users, passwords, publisher);
    }

    @Test
    void nomeComMaisDe100CaracteresERejeitado() {
        assertInvalidData(() -> service.register(request("A".repeat(101), null, CPF_VALIDO, "a@b.com", null)));
        verifyNoInteractions(users);
    }

    @Test
    void sobrenomeComMaisDe100CaracteresERejeitado() {
        assertInvalidData(() -> service.register(request("Jefferson", "B".repeat(101), CPF_VALIDO, "a@b.com", null)));
        verifyNoInteractions(users);
    }

    @Test
    void nomeE100CaracteresEAceito() {
        persistindoComId("user-1");

        service.register(request("A".repeat(100), "B".repeat(100), CPF_VALIDO, "a@b.com", null));

        assertThat(usuarioSalvo().getNome()).hasSize(100);
    }

    // ---------- telefone ----------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void telefoneAusenteESalvoComoNulo(String telefone) {
        persistindoComId("user-1");

        service.register(request("Jefferson", "Vitena", CPF_VALIDO, "a@b.com", telefone));

        assertThat(usuarioSalvo().getTelefone()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"7533334444", "+55 (75) 99999-9999"})
    void telefoneEntre10E13DigitosEAceito(String telefone) {
        persistindoComId("user-1");

        service.register(request("Jefferson", "Vitena", CPF_VALIDO, "a@b.com", telefone));

        assertThat(usuarioSalvo().getTelefone()).matches("\\d{10,13}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"999999999", "12345678901234", "telefone"})
    void telefoneForaDoTamanhoERejeitado(String telefone) {
        assertInvalidData(() -> service.register(request("Jefferson", "Vitena", CPF_VALIDO, "a@b.com", telefone)));
        verifyNoInteractions(users);
    }

    // ---------- CPF ----------

    @ParameterizedTest
    @ValueSource(strings = {"12345678901", "11111111111", "5299822472", "abc"})
    void cpfInvalidoERejeitadoSemConsultarBanco(String cpf) {
        assertThatThrownBy(() -> service.register(request("Jefferson", "Vitena", cpf, "a@b.com", null)))
                .isInstanceOfSatisfying(ApiBusinessException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getCode()).isEqualTo("INVALID_CPF");
                });
        verifyNoInteractions(users, passwords, publisher);
    }

    // ---------- duplicidade ----------

    @Test
    void cpfJaCadastradoRetornaConflitoSemSalvar() {
        when(users.existsByCpf(CPF_VALIDO)).thenReturn(true);

        assertThatThrownBy(() -> service.register(requestValida()))
                .isInstanceOfSatisfying(ApiBusinessException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCode()).isEqualTo("CPF_ALREADY_REGISTERED");
                });
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(passwords, publisher);
    }

    @Test
    void emailJaCadastradoRetornaConflitoSemSalvar() {
        when(users.existsByCpf(CPF_VALIDO)).thenReturn(false);
        when(users.existsByEmailIgnoreCase("jefferson@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(requestValida()))
                .isInstanceOfSatisfying(ApiBusinessException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCode()).isEqualTo("EMAIL_ALREADY_REGISTERED");
                });
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(passwords, publisher);
    }

    // ---------- Termos de Uso e Política de Privacidade ----------

    @Test
    void semDocumentosLegaisConfiguradosNaoRegistraAceite() {
        persistindoComId("user-1");

        service.register(request("Jefferson", "Vitena", CPF_VALIDO, "a@b.com", null, false, false, null, null));

        User salvo = usuarioSalvo();
        assertThat(salvo.getTermsVersion()).isNull();
        assertThat(salvo.getPrivacyVersion()).isNull();
        assertThat(salvo.getTermsAcceptedAt()).isNull();
        assertThat(salvo.getPrivacyAcceptedAt()).isNull();
    }

    @Test
    void comDocumentosLegaisConfiguradosRegistraVersoesEDataDoAceite() {
        service = serviceCom(legalConfigurado());
        persistindoComId("user-1");
        Instant antes = Instant.now();

        ClientRegistrationResponse response = service.register(
                request("Jefferson", "Vitena", CPF_VALIDO, "a@b.com", null, true, true, TERMOS_V, PRIVACIDADE_V));

        User salvo = usuarioSalvo();
        assertThat(salvo.getTermsVersion()).isEqualTo(TERMOS_V);
        assertThat(salvo.getPrivacyVersion()).isEqualTo(PRIVACIDADE_V);
        assertThat(salvo.getTermsAcceptedAt()).isNotNull().isAfterOrEqualTo(antes);
        assertThat(salvo.getPrivacyAcceptedAt()).isEqualTo(salvo.getTermsAcceptedAt());
        assertThat(response.legalDocumentsConfigured()).isTrue();
    }

    @Test
    void comDocumentosLegaisConfiguradosExigeAceiteDosTermos() {
        service = serviceCom(legalConfigurado());

        assertInvalidData(() -> service.register(
                request("Jefferson", "Vitena", CPF_VALIDO, "a@b.com", null, false, true, TERMOS_V, PRIVACIDADE_V)));
        verifyNoInteractions(users, passwords, publisher);
    }

    @Test
    void comDocumentosLegaisConfiguradosExigeAceiteDaPrivacidade() {
        service = serviceCom(legalConfigurado());

        assertInvalidData(() -> service.register(
                request("Jefferson", "Vitena", CPF_VALIDO, "a@b.com", null, true, null, TERMOS_V, PRIVACIDADE_V)));
        verifyNoInteractions(users);
    }

    @Test
    void comDocumentosLegaisConfiguradosRejeitaVersaoDesatualizada() {
        service = serviceCom(legalConfigurado());

        assertInvalidData(() -> service.register(
                request("Jefferson", "Vitena", CPF_VALIDO, "a@b.com", null, true, true, "2025-01", PRIVACIDADE_V)));
        verifyNoInteractions(users);
    }

    // ---------- helpers ----------

    private RegistrationService serviceCom(LegalDocumentsService legal) {
        return new RegistrationService(users, passwords, new CpfService(), legal, publisher);
    }

    private static LegalDocumentsService legalNaoConfigurado() {
        return new LegalDocumentsService("", "", "", "");
    }

    private static LegalDocumentsService legalConfigurado() {
        return new LegalDocumentsService("https://app.exemplo.com/termos", "https://app.exemplo.com/privacidade",
                TERMOS_V, PRIVACIDADE_V);
    }

    /** Simula o banco: sem duplicidade, senha codificada e ID gerado no saveAndFlush. */
    private void persistindoComId(String id) {
        when(users.existsByCpf(any())).thenReturn(false);
        when(users.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(passwords.encode(any())).thenReturn("hash-da-senha");
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setIdUser(id);
            return user;
        });
    }

    private User usuarioSalvo() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static void assertInvalidData(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiBusinessException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getCode()).isEqualTo("INVALID_REGISTRATION_DATA");
        });
    }

    private static ClientRegistrationRequest requestValida() {
        return request("Jefferson Henrique", "Vitena", CPF_VALIDO, "jefferson@example.com", "75999999999");
    }

    private static ClientRegistrationRequest request(String nome, String sobrenome, String cpf, String email,
                                                     String telefone) {
        return request(nome, sobrenome, cpf, email, telefone, true, true, null, null);
    }

    private static ClientRegistrationRequest request(String nome, String sobrenome, String cpf, String email,
                                                     String telefone, Boolean termos, Boolean privacidade,
                                                     String termosVersao, String privacidadeVersao) {
        return new ClientRegistrationRequest(nome, sobrenome, cpf, email, telefone, "SenhaSegura123",
                termos, privacidade, termosVersao, privacidadeVersao);
    }
}
