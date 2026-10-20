package com.jefiro.app247.infra.service;

import com.jefiro.app247.infra.exception.ApiBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * Políticas de limite das rotas públicas de autenticação. O limite por identidade
 * (CPF/e-mail) é o que segura força bruta; o por origem só reduz volume, porque o IP
 * pode ser forjado enquanto não houver proxy confiável sobrescrevendo X-Forwarded-For.
 */
@Service
public class PublicAuthRateLimits {
    private static final Logger log = LoggerFactory.getLogger(PublicAuthRateLimits.class);

    @Autowired private SecurityRateLimiter limiter;

    @Value("${app.security.login.window:PT15M}") private Duration loginWindow;
    @Value("${app.security.login.max-failures-per-cpf:5}") private long loginFailuresPerCpf;
    @Value("${app.security.login.max-attempts-per-origin:50}") private long loginAttemptsPerOrigin;

    @Value("${app.security.email-code.window:PT15M}") private Duration emailCodeWindow;
    @Value("${app.security.email-code.send-per-email:3}") private long sendPerEmail;
    @Value("${app.security.email-code.send-per-origin:20}") private long sendPerOrigin;
    @Value("${app.security.email-code.validate-per-email:5}") private long validatePerEmail;

    @Value("${app.security.onboarding.window:PT1H}") private Duration onboardingWindow;
    @Value("${app.security.onboarding.per-origin:5}") private long onboardingPerOrigin;

    @Value("${app.security.terminal-pairing.window:PT1H}") private Duration pairingWindow;
    @Value("${app.security.terminal-pairing.start-per-origin:30}") private long pairingStartPerOrigin;
    @Value("${app.security.terminal-pairing.approval-failures-per-company:10}") private long pairingFailuresPerCompany;

    /** Antes de autenticar: CPF com falhas demais fica bloqueado até a janela expirar. */
    public void beforeLogin(String origin, String cpf) {
        if (limiter.isExhausted("login-failure-cpf", normalizeCpf(cpf), loginFailuresPerCpf)
                || !limiter.tryAcquire("login-origin", origin, loginAttemptsPerOrigin, loginWindow)) {
            log.warn("[AUTH-RATE] operation=LOGIN status=RATE_LIMITED");
            throw limited("LOGIN_RATE_LIMITED", "Muitas tentativas de login. Tente novamente mais tarde.");
        }
    }

    public void loginFailed(String cpf) {
        limiter.tryAcquire("login-failure-cpf", normalizeCpf(cpf), loginFailuresPerCpf, loginWindow);
    }

    public void loginSucceeded(String cpf) {
        limiter.reset("login-failure-cpf", normalizeCpf(cpf));
    }

    public void beforeSendCode(String origin, String email) {
        if (!limiter.tryAcquire("email-code-send-email", normalizeEmail(email), sendPerEmail, emailCodeWindow)
                || !limiter.tryAcquire("email-code-send-origin", origin, sendPerOrigin, emailCodeWindow)) {
            log.warn("[AUTH-RATE] operation=SEND_EMAIL_CODE status=RATE_LIMITED");
            throw limited("EMAIL_CODE_RATE_LIMITED", "Muitas solicitações de código. Tente novamente mais tarde.");
        }
    }

    /** @return false quando as tentativas do e-mail acabaram; o chamador deve invalidar o código. */
    public boolean tryValidateCode(String email) {
        return limiter.tryAcquire("email-code-validate-email", normalizeEmail(email), validatePerEmail, emailCodeWindow);
    }

    public void codeValidated(String email) {
        limiter.reset("email-code-validate-email", normalizeEmail(email));
    }

    public ApiBusinessException validateCodeLimited() {
        log.warn("[AUTH-RATE] operation=VALIDATE_EMAIL_CODE status=RATE_LIMITED");
        return limited("EMAIL_CODE_RATE_LIMITED", "Muitas tentativas. Solicite um novo código mais tarde.");
    }

    public void beforeOnboarding(String origin) {
        if (!limiter.tryAcquire("onboarding-origin", origin, onboardingPerOrigin, onboardingWindow)) {
            log.warn("[AUTH-RATE] operation=ONBOARDING status=RATE_LIMITED");
            throw limited("ONBOARDING_RATE_LIMITED", "Muitos cadastros a partir desta origem. Tente novamente mais tarde.");
        }
    }

    /** Terminal pedindo código: renova a cada 15 min e vários equipamentos podem dividir o mesmo NAT. */
    public void beforeTerminalPairing(String origin) {
        if (!limiter.tryAcquire("terminal-pairing-origin", origin, pairingStartPerOrigin, pairingWindow)) {
            log.warn("[AUTH-RATE] operation=TERMINAL_PAIRING_START status=RATE_LIMITED");
            throw limited("PAIRING_RATE_LIMITED", "Muitos pedidos de código de pareamento. Tente novamente mais tarde.");
        }
    }

    /** Códigos errados por Empresa: o código é a única barreira contra adivinhação na aprovação. */
    public void beforeTerminalPairingApproval(String empresaId) {
        if (limiter.isExhausted("terminal-pairing-failure", empresaId, pairingFailuresPerCompany)) {
            log.warn("[AUTH-RATE] operation=TERMINAL_PAIRING_APPROVE status=RATE_LIMITED");
            throw limited("PAIRING_RATE_LIMITED", "Muitos códigos inválidos. Tente novamente mais tarde.");
        }
    }

    public void terminalPairingApprovalFailed(String empresaId) {
        limiter.tryAcquire("terminal-pairing-failure", empresaId, pairingFailuresPerCompany, pairingWindow);
    }

    private static ApiBusinessException limited(String code, String message) {
        return new ApiBusinessException(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }

    private static String normalizeCpf(String cpf) {
        return cpf == null ? "" : cpf.replaceAll("\\D", "");
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
