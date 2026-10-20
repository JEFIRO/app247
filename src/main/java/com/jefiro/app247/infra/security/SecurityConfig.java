package com.jefiro.app247.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.infra.exception.RestErrorMessage;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Autowired
    private SecurityFilter securityFilter;
    @Autowired
    private TerminalHttpAuthenticationFilter terminalHttpAuthenticationFilter;
    @Autowired
    private UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity, ObjectMapper objectMapper) {
        return httpSecurity.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable).sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)

                ).authorizeHttpRequests(authorization -> authorization
                        .requestMatchers(
                                "/mercado-pago/oauth/callback", "/mp/oauth/callback",
                                "/public/leads", "/public/app-config",
                                "/", "/error", "/actuator/health", "/terminal/health",
                                "/terminal/serial/*", "/webhook/mercadopago",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/onboarding", "/condominio").permitAll()
                        .requestMatchers(HttpMethod.POST, "/terminal/pairing", "/terminal/pairing/claim").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/auth/login/admin",
                                "/auth/send-code", "/auth/validate-code").permitAll()
                        .requestMatchers(HttpMethod.POST, "/user/recuperar", "/user/validar",
                                "/user/redefinir-senha").permitAll()
                        .requestMatchers("/auth/**").denyAll()
                        .requestMatchers("/checkout", "/checkout/session").denyAll()
                        .requestMatchers(
                                "/terminal-socket", "/payment-socket/**", "/ws/**",
                                "/produtos/sync", "/produtos/home", "/terminais/*/produtos-disponiveis",
                                "/terminal/telemetry", "/terminal/*/bootstrap", "/terminal/*/factory-reset/**",
                                "/terminal/customer-link/consume", "/terminal/coupons/apply",
                                "/carrinho/**", "/order/**", "/pagamento/terminal/**",
                                "/checkout/carrinho", "/checkout/qrcode", "/comprovante/**"
                        ).hasRole("TERMINAL")
                        .requestMatchers(
                                "/admin/**", "/empresas/**", "/condominios/**", "/terminais/**",
                                "/produtos/**", "/promocoes/**", "/estoque-empresa/**",
                                "/estoque/geral", "/transferencias-estoque/**", "/inventarios/**",
                                "/planogramas/**", "/mercado-pago/**",
                                "/mp/oauth/mercadopago/**", "/mp/oauth/terminal/**",
                                "/api/testes/mercadopago/**"
                        ).hasAnyRole("ADMIN", "GERENTE")
                        .requestMatchers("/app-cliente/**").hasRole("USER")
                        .requestMatchers("/user/**").hasRole("USER")
                        .anyRequest().denyAll()
                )
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, exception) -> {
                            Object failureCode = request.getAttribute(SecurityFilter.FAILURE_CODE_ATTRIBUTE);
                            String code = failureCode instanceof String value ? value : "AUTH_REQUIRED";
                            String message = switch (code) {
                                case "COMPANY_DISABLED" -> "Empresa desativada ou encerrada";
                                case "COMPANY_NOT_FOUND" -> "Empresa do token não está disponível";
                                case "USER_DISABLED" -> "Usuário desativado";
                                case "TERMINAL_AUTH_INVALID" -> "Credencial do Terminal inválida";
                                case "TOKEN_INVALID" -> "Token inválido ou expirado";
                                case "AUTH_REQUIRED" -> "Autenticação obrigatória";
                                default -> "Acesso negado";
                            };
                            HttpStatus status = code.equals("TERMINAL_AUTH_INVALID")
                                    || code.equals("TOKEN_INVALID")
                                    || code.equals("AUTH_REQUIRED")
                                    ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
                            response.setStatus(status.value());
                            response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), new RestErrorMessage(
                                    status, message, code, null));
                        }))
                .addFilterBefore(terminalHttpAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {

        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
