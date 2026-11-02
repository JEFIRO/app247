package com.jefiro.app247.infra.security;

import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.service.TerminalCredentialAnomalyService;
import com.jefiro.app247.infra.service.TerminalRequestAuthorizationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Defesa HTTP de primeira camada para rotas operacionais autenticadas por dispositivo. */
@Component
public class TerminalHttpAuthenticationFilter extends OncePerRequestFilter {
    public static final String FAILURE_CODE = "TERMINAL_AUTH_INVALID";
    public static final String UNAVAILABLE_CODE = "TERMINAL_AUTH_UNAVAILABLE";
    private static final Logger log = LoggerFactory.getLogger(TerminalHttpAuthenticationFilter.class);
    @Autowired private TerminalRequestAuthorizationService authorization;
    @Autowired private TerminalCredentialAnomalyService anomalies;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/terminal-socket") || path.startsWith("/payment-socket/")
                || path.equals("/ws") || path.startsWith("/ws/")
                || path.equals("/produtos/sync") || path.equals("/produtos/home")
                || path.matches("/terminais/[^/]+/produtos-disponiveis")
                || path.equals("/terminal/telemetry")
                || path.matches("/terminal/[^/]+/(bootstrap|factory-reset/started|factory-reset/completed)")
                || path.equals("/terminal/customer-link/consume")
                || path.equals("/terminal/coupons/apply")
                || path.equals("/carrinho") || path.startsWith("/carrinho/")
                || path.equals("/order") || path.startsWith("/order/")
                || path.startsWith("/pagamento/terminal/")
                || path.equals("/checkout/carrinho") || path.equals("/checkout/qrcode")
                || path.equals("/comprovante") || path.startsWith("/comprovante/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String credential = request.getHeader("X-Terminal-Token");
        try {
            AuthenticatedTerminal principal = authorization.authenticateHttp(credential);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_TERMINAL"))));
        } catch (ApiBusinessException invalid) {
            request.setAttribute(SecurityFilter.FAILURE_CODE_ATTRIBUTE,
                    credential == null || credential.isBlank() ? "AUTH_REQUIRED" : FAILURE_CODE);
        } catch (RuntimeException unavailable) {
            // Banco fora não prova credencial inválida: 401 faria o Terminal se bloquear.
            log.warn("[TERMINAL-AUTH] autenticação indisponível path={} cause={}",
                    request.getRequestURI(), unavailable.getClass().getSimpleName());
            request.setAttribute(SecurityFilter.FAILURE_CODE_ATTRIBUTE, UNAVAILABLE_CODE);
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedTerminal principal) {
            observe(principal, request);
        }
        chain.doFilter(request, response);
    }

    /** O alerta de origem nunca decide a autenticação. */
    private void observe(AuthenticatedTerminal principal, HttpServletRequest request) {
        try {
            anomalies.observe(principal, request.getRemoteAddr());
        } catch (RuntimeException ignored) {
            log.debug("[TERMINAL-ANOMALY] observação ignorada: {}", ignored.getClass().getSimpleName());
        }
    }
}
