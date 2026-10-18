package com.jefiro.app247.infra.security;

import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.infra.service.EmpresaContext;
import com.jefiro.app247.infra.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {
    public static final String FAILURE_CODE_ATTRIBUTE = SecurityFilter.class.getName() + ".failureCode";

    @Autowired
    TokenService tokenService;
    @Autowired
    UserRepository repository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        var token = recoverToken(request);
        try {

            TokenService.TokenIdentity tokenIdentity = null;
            if (token != null) {
                try {
                    tokenIdentity = tokenService.validateIdentity(token);
                } catch (RuntimeException invalid) {
                    // Assinatura inválida ou token expirado: segue anônimo; rota protegida responde 401.
                    request.setAttribute(FAILURE_CODE_ATTRIBUTE, "TOKEN_INVALID");
                }
            }
            if (tokenIdentity != null) {
                var identity = repository.findSecurityIdentityByCpf(tokenIdentity.subject()).orElse(null);

                if (identity != null && identidadeConfereComToken(identity, tokenIdentity, request)) {
                    var user = identity.user();
                    if (identity.empresaId() != null) EmpresaContext.set(identity.empresaId());

                    var authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user,
                                    null,
                                    user.getAuthorities());

                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            EmpresaContext.clear();
        }
    }

    private boolean identidadeConfereComToken(SecurityIdentity identity,
                                               TokenService.TokenIdentity tokenIdentity,
                                               HttpServletRequest request) {
        if (!Boolean.TRUE.equals(identity.usuarioAtivo())) {
            request.setAttribute(FAILURE_CODE_ATTRIBUTE, "USER_DISABLED");
            return false;
        }
        if (tokenIdentity.userId() == null || !tokenIdentity.userId().equals(identity.user().getIdUser())) {
            request.setAttribute(FAILURE_CODE_ATTRIBUTE, "COMPANY_NOT_FOUND");
            return false;
        }
        if (identity.empresaId() == null) {
            boolean globalClient = identity.user().getRole() == RoleUser.USER && tokenIdentity.empresaId() == null;
            if (!globalClient) request.setAttribute(FAILURE_CODE_ATTRIBUTE, "COMPANY_NOT_FOUND");
            return globalClient;
        }
        if (tokenIdentity.empresaId() == null || !tokenIdentity.empresaId().equals(identity.empresaId())) {
            request.setAttribute(FAILURE_CODE_ATTRIBUTE, "COMPANY_NOT_FOUND");
            return false;
        }
        if (!Boolean.TRUE.equals(identity.empresaAtiva()) || identity.empresaEncerradaEm() != null) {
            request.setAttribute(FAILURE_CODE_ATTRIBUTE, "COMPANY_DISABLED");
            return false;
        }
        return true;
    }


    private String recoverToken(HttpServletRequest request) {

        var authHeader = request.getHeader("Authorization");

        if (authHeader == null) {
            return null;
        }

        return authHeader.replace("Bearer ", "");
    }
}
