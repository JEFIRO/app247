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

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Autowired
    private SecurityFilter securityFilter;
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
                                "/produtos/sync", "/produtos/home", "/public/leads"
                        ).permitAll()
                        .requestMatchers(
                                "/admin/**", "/empresas/**", "/condominios/**", "/terminais/**",
                                "/produtos/**", "/promocoes/**", "/estoque-empresa/**",
                                "/estoque/geral", "/transferencias-estoque/**", "/inventarios/**",
                                "/planogramas/**", "/mercado-pago/**",
                                "/mp/oauth/mercadopago/**", "/mp/oauth/terminal/**"
                        ).hasAnyRole("ADMIN", "GERENTE")
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, exception) -> {
                            Object failureCode = request.getAttribute(SecurityFilter.FAILURE_CODE_ATTRIBUTE);
                            if (!(failureCode instanceof String code)) {
                                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                                return;
                            }
                            String message = switch (code) {
                                case "COMPANY_DISABLED" -> "Empresa desativada ou encerrada";
                                case "COMPANY_NOT_FOUND" -> "Empresa do token não está disponível";
                                case "USER_DISABLED" -> "Usuário desativado";
                                default -> "Acesso negado";
                            };
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), new RestErrorMessage(
                                    HttpStatus.FORBIDDEN, message, code, null));
                        }))
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
