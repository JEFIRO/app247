package com.jefiro.app247.infra.security;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.infra.service.EmpresaContext;
import com.jefiro.app247.infra.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {
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

            if (token != null) {

                var subject = tokenService.validate(token);
                UserDetails user = repository.findByCpf(subject);
                if (user != null) {
                    User ur = (User) user;

                    if (ur.getEmpresa() != null) {
                        EmpresaContext.set(ur.getEmpresa().getId());
                    }

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


    private String recoverToken(HttpServletRequest request) {

        var authHeader = request.getHeader("Authorization");

        if (authHeader == null) {
            return null;
        }

        return authHeader.replace("Bearer ", "");
    }
}
