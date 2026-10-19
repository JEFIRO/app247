package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.auth.ClientRegistrationRequest;
import com.jefiro.app247.domain.model.dto.auth.ClientRegistrationResponse;
import com.jefiro.app247.infra.event.UserCreatedEvent;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class RegistrationService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final CpfService cpfs;
    private final LegalDocumentsService legal;
    private final ApplicationEventPublisher publisher;

    public RegistrationService(UserRepository users, PasswordEncoder passwords, CpfService cpfs,
                               LegalDocumentsService legal, ApplicationEventPublisher publisher) {
        this.users = users;
        this.passwords = passwords;
        this.cpfs = cpfs;
        this.legal = legal;
        this.publisher = publisher;
    }

    @Transactional
    public ClientRegistrationResponse register(ClientRegistrationRequest request) {
        String cpf = cpfs.requireValid(request.cpf());
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        NameParts name = names(request.nome(), request.sobrenome());
        String phone = normalizePhone(request.telefone());
        legal.validateAcceptance(request);

        if (users.existsByCpf(cpf)) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "CPF_ALREADY_REGISTERED",
                    "Já existe uma conta cadastrada com este CPF.");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                    "Este e-mail já está sendo utilizado.");
        }
        User user = User.builder()
                .nome(name.first()).sobrenome(name.last()).cpf(cpf).email(email).telefone(phone)
                .senha(passwords.encode(request.senha())).role(RoleUser.USER).ativo(true)
                .emailVerificado(false).cpfVerifiedAt(null).build();

        if (legal.configured()) {
            Instant now = Instant.now();
            user.setTermsVersion(legal.termsVersion());
            user.setPrivacyVersion(legal.privacyVersion());
            user.setTermsAcceptedAt(now);
            user.setPrivacyAcceptedAt(now);
        }
        User saved = users.saveAndFlush(user);

        publisher.publishEvent(new UserCreatedEvent(saved));
        return new ClientRegistrationResponse(
                new ClientRegistrationResponse.UserSummary(saved.getIdUser(), fullName(saved), saved.getEmail()),
                "UNVERIFIED", null, true, legal.configured());
    }

    private static NameParts names(String rawName, String rawLastName) {
        String full = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (full.isBlank()) throw invalidData("Nome completo é obrigatório.");
        String explicitLast = rawLastName == null ? "" : rawLastName.trim().replaceAll("\\s+", " ");
        String first;
        String last;
        if (!explicitLast.isBlank()) {
            first = full;
            last = explicitLast;
        } else {
            int split = full.indexOf(' ');
            first = split < 0 ? full : full.substring(0, split);
            last = split < 0 ? null : full.substring(split + 1);
        }
        if (first.length() > 100 || (last != null && last.length() > 100)) {
            throw invalidData("Nome completo excede o limite permitido.");
        }
        return new NameParts(first, last);
    }

    private static String normalizePhone(String value) {
        if (value == null || value.isBlank()) return null;
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 10 || digits.length() > 13) {
            throw invalidData("Telefone inválido.");
        }
        return digits;
    }

    private static ApiBusinessException invalidData(String message) {
        return new ApiBusinessException(HttpStatus.BAD_REQUEST, "INVALID_REGISTRATION_DATA", message);
    }

    private static String fullName(User user) {
        return user.getSobrenome() == null ? user.getNome() : user.getNome() + " " + user.getSobrenome();
    }

    private record NameParts(String first, String last) {}
}
