package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.ValidateEmailRequest;
import com.jefiro.app247.domain.model.dto.auth.AuthDTO;
import com.jefiro.app247.domain.model.dto.auth.ClientRegistrationRequest;
import com.jefiro.app247.domain.model.dto.auth.ClientRegistrationResponse;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.exception.InvalidPasswordException;
import com.jefiro.app247.infra.service.PublicAuthRateLimits;
import com.jefiro.app247.infra.service.RegistrationRateLimiter;
import com.jefiro.app247.infra.service.RegistrationService;
import com.jefiro.app247.infra.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.function.Supplier;

@RestController
@RequestMapping("auth")
public class AuthController {
    private final UserService users;
    private final RegistrationService registrations;
    private final RegistrationRateLimiter rateLimiter;
    private final PublicAuthRateLimits authLimits;

    public AuthController(UserService users, RegistrationService registrations,
                          RegistrationRateLimiter rateLimiter, PublicAuthRateLimits authLimits) {
        this.users = users;
        this.registrations = registrations;
        this.rateLimiter = rateLimiter;
        this.authLimits = authLimits;
    }

    @PostMapping("/login")
    @ApiResponse(responseCode = "429", description = "LOGIN_RATE_LIMITED")
    public ResponseEntity<?> login(@RequestBody @Valid AuthDTO auth, HttpServletRequest request) {
        return ResponseEntity.ok(limitedLogin(auth, request, () -> users.login(auth)));
    }

    @PostMapping("/login/admin")
    @ApiResponse(responseCode = "429", description = "LOGIN_RATE_LIMITED")
    public ResponseEntity<?> loginAdmin(@RequestBody @Valid AuthDTO auth, HttpServletRequest request) {
        return ResponseEntity.ok(limitedLogin(auth, request, () -> users.loginAdmin(auth)));
    }

    @PostMapping("/register")
    @Operation(summary = "Cria uma conta do App 24/7 Cliente sem vínculo arbitrário de tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conta criada; CPF ainda não verificado"),
            @ApiResponse(responseCode = "400", description = "Dados ou CPF inválidos"),
            @ApiResponse(responseCode = "409", description = "CPF ou e-mail já cadastrado"),
            @ApiResponse(responseCode = "429", description = "Limite de tentativas excedido")
    })
    public ResponseEntity<ClientRegistrationResponse> create(
            @RequestBody @Valid ClientRegistrationRequest body, HttpServletRequest request) {
        if (!rateLimiter.allow(clientIp(request), body.email(), body.cpf())) {
            throw new ApiBusinessException(HttpStatus.TOO_MANY_REQUESTS, "REGISTRATION_RATE_LIMITED",
                    "Muitas tentativas de cadastro. Tente novamente mais tarde.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(registrations.register(body));
    }

    @PostMapping("/send-code")
    @ApiResponse(responseCode = "429", description = "EMAIL_CODE_RATE_LIMITED")
    public ResponseEntity<?> sendCode(
            @RequestBody @Validated(ValidateEmailRequest.SendCode.class) ValidateEmailRequest request,
            HttpServletRequest httpRequest) {
        authLimits.beforeSendCode(clientIp(httpRequest), request.getEmail());
        users.sendCode(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/validate-code")
    @ApiResponse(responseCode = "429", description = "EMAIL_CODE_RATE_LIMITED")
    public ResponseEntity<?> validateCode(
            @RequestBody @Validated(ValidateEmailRequest.VerifyCode.class) ValidateEmailRequest request) {
        if (!authLimits.tryValidateCode(request.getEmail())) {
            // Esgotou as tentativas: o código atual deixa de valer, exigindo um novo envio.
            users.invalidarCode(request.getEmail());
            throw authLimits.validateCodeLimited();
        }
        users.verificarCode(request);
        authLimits.codeValidated(request.getEmail());
        return ResponseEntity.ok().build();
    }

    private <T> T limitedLogin(AuthDTO auth, HttpServletRequest request, Supplier<T> login) {
        authLimits.beforeLogin(clientIp(request), auth.cpf());
        try {
            T response = login.get();
            authLimits.loginSucceeded(auth.cpf());
            return response;
        } catch (InvalidPasswordException failure) {
            authLimits.loginFailed(auth.cpf());
            throw failure;
        }
    }

    // Com server.forward-headers-strategy=framework, getRemoteAddr já reflete X-Forwarded-For.
    // Só é confiável atrás de um proxy que sobrescreva esse header (nginx).
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
