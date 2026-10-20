package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.dto.TerminalCredentialResponse;
import com.jefiro.app247.domain.model.enum_type.TerminalLifecycleState;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.TerminalRepository;
import com.jefiro.app247.infra.security.AuthenticatedTerminal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/** Autentica a credencial individual e vincula toda operação ao Terminal persistido. */
@Service
public class TerminalRequestAuthorizationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CREDENTIAL_BYTES = 32;

    @Autowired private TerminalRepository terminals;

    @Transactional(readOnly = true)
    public Terminal authorize(String credential, String terminalId) {
        Terminal terminal = authenticateDevice(credential);
        requireSameTerminal(terminal, terminalId);
        requireOperational(terminal);
        return terminal;
    }

    @Transactional(readOnly = true)
    public AuthenticatedTerminal authenticateChannel(String credential) {
        Terminal terminal = authenticateDevice(credential);
        requireOperational(terminal);
        return principal(terminal);
    }

    @Transactional(readOnly = true)
    public AuthenticatedTerminal authenticateHttp(String credential) {
        return principal(authenticateDevice(credential));
    }

    @Transactional(readOnly = true)
    public AuthenticatedTerminal authorizeChannel(String credential, String terminalId) {
        return principal(authorize(credential, terminalId));
    }

    @Transactional(readOnly = true)
    public AuthenticatedTerminal authorizeLifecycle(String credential, String terminalId) {
        Terminal terminal = authenticateDevice(credential);
        requireSameTerminal(terminal, terminalId);
        return principal(terminal);
    }

    @Transactional(readOnly = true)
    public AuthenticatedTerminal authorizeFactoryReset(String credential, String terminalId) {
        Terminal terminal = authenticateDevice(credential);
        requireSameTerminal(terminal, terminalId);
        if (terminal.getLifecycleState() != TerminalLifecycleState.RESET_REQUIRED) {
            throw new ApiBusinessException(HttpStatus.FORBIDDEN, "TERMINAL_RESET_NOT_REQUIRED",
                    "Terminal não está autorizado a confirmar factory reset");
        }
        return principal(terminal);
    }

    @Transactional(readOnly = true)
    public AuthenticatedTerminal revalidate(AuthenticatedTerminal identity) {
        Terminal terminal = revalidateIdentity(identity);
        requireOperational(terminal);
        return principal(terminal);
    }

    @Transactional(readOnly = true)
    public AuthenticatedTerminal revalidateLifecycle(AuthenticatedTerminal identity) {
        return principal(revalidateIdentity(identity));
    }

    @Transactional
    public TerminalCredentialResponse provision(String terminalId) {
        Terminal terminal = credentialTerminalForCurrentTenant(terminalId);
        if (terminal.getDeviceCredentialHash() != null
                && terminal.getDeviceCredentialRevokedAt() == null) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "TERMINAL_CREDENTIAL_EXISTS",
                    "Terminal já possui credencial ativa");
        }
        return issue(terminal);
    }

    @Transactional
    public TerminalCredentialResponse rotate(String terminalId) {
        Terminal terminal = credentialTerminalForCurrentTenant(terminalId);
        if (terminal.getDeviceCredentialHash() == null) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "TERMINAL_CREDENTIAL_MISSING",
                    "Terminal ainda não possui credencial");
        }
        return issue(terminal);
    }

    /**
     * Emite a credencial de um Terminal recém-pareado. Chamado pelo próprio equipamento
     * (sem tenant no contexto): o vínculo foi aprovado antes por um gestor da Empresa, e o
     * serial precisa continuar sendo o da sessão aprovada. Substitui qualquer credencial anterior.
     */
    @Transactional
    public TerminalCredentialResponse issueForPairing(String terminalId, String serialNumber) {
        Terminal terminal = terminals.findByIdForCredentialUpdate(terminalId).orElseThrow(() ->
                new ApiBusinessException(HttpStatus.CONFLICT, "TERMINAL_PAIRING_FAILED",
                        "Pareamento não pode ser concluído; gere um novo código"));
        if (serialNumber == null || !serialNumber.equals(terminal.getSerialNumber())) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "TERMINAL_PAIRING_FAILED",
                    "Pareamento não pode ser concluído; gere um novo código");
        }
        requireOperational(terminal);
        return issue(terminal);
    }

    @Transactional
    public void revoke(String terminalId) {
        Terminal terminal = credentialTerminalForCurrentTenant(terminalId);
        if (terminal.getDeviceCredentialHash() == null || terminal.getDeviceCredentialRevokedAt() != null) return;
        terminal.setDeviceCredentialRevokedAt(Instant.now());
        terminals.save(terminal);
    }

    public void requireCartOwnership(Terminal terminal, Carrinho cart) {
        if (terminal == null || cart == null || cart.getTerminal() == null
                || !terminal.getIdTerminal().equals(cart.getTerminal().getIdTerminal())
                || cart.getEmpresa() == null || cart.getCondominio() == null
                || !terminal.getCondominio().getEmpresa().getId().equals(cart.getEmpresa().getId())
                || !terminal.getCondominio().getIdCondominio().equals(cart.getCondominio().getIdCondominio())) {
            throw new ApiBusinessException(HttpStatus.NOT_FOUND, "CART_NOT_OWNED",
                    "Carrinho não pertence ao Terminal autenticado");
        }
    }

    public void requireOrderOwnership(Terminal terminal, Order order) {
        if (order == null || order.getTerminal() == null
                || !terminal.getIdTerminal().equals(order.getTerminal().getIdTerminal())
                || order.getEmpresa() == null || order.getCondominio() == null
                || !terminal.getCondominio().getEmpresa().getId().equals(order.getEmpresa().getId())
                || !terminal.getCondominio().getIdCondominio().equals(order.getCondominio().getIdCondominio())) {
            throw new ApiBusinessException(HttpStatus.NOT_FOUND, "ORDER_NOT_OWNED",
                    "Order não pertence ao Terminal autenticado");
        }
    }

    private Terminal authenticateDevice(String credential) {
        if (credential == null || credential.isBlank()) throw unauthorized();
        Terminal terminal = terminals.findByDeviceCredentialHash(hash(credential)).orElseThrow(this::unauthorized);
        if (terminal.getDeviceCredentialRevokedAt() != null) throw unauthorized();
        return terminal;
    }

    private Terminal revalidateIdentity(AuthenticatedTerminal identity) {
        if (identity == null) throw unauthorized();
        Terminal terminal = terminals.findByIdWithTenant(identity.terminalId()).orElseThrow(this::unauthorized);
        if (terminal.getDeviceCredentialHash() == null
                || terminal.getDeviceCredentialRevokedAt() != null
                || terminal.getDeviceCredentialVersion() == null
                || terminal.getDeviceCredentialVersion() != identity.credentialVersion()
                || !terminal.getCondominio().getIdCondominio().equals(identity.condominioId())
                || !terminal.getCondominio().getEmpresa().getId().equals(identity.empresaId())) {
            throw unauthorized();
        }
        return terminal;
    }

    private void requireSameTerminal(Terminal terminal, String terminalId) {
        if (terminalId == null || terminalId.isBlank()) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, "TERMINAL_ID_REQUIRED",
                    "Identificador do Terminal é obrigatório");
        }
        if (!terminal.getIdTerminal().equals(terminalId)) {
            throw new ApiBusinessException(HttpStatus.FORBIDDEN, "TERMINAL_IDENTITY_MISMATCH",
                    "Credencial não autoriza o Terminal informado");
        }
    }

    private void requireOperational(Terminal terminal) {
        if (!Boolean.TRUE.equals(terminal.getAtivo())
                || terminal.getLifecycleState() != TerminalLifecycleState.ACTIVE
                || terminal.getCondominio() == null
                || !Boolean.TRUE.equals(terminal.getCondominio().getAtivo())
                || terminal.getCondominio().getEmpresa() == null
                || !Boolean.TRUE.equals(terminal.getCondominio().getEmpresa().getAtivo())
                || terminal.getCondominio().getEmpresa().isDefinitivamenteEncerrada()) {
            throw new ApiBusinessException(HttpStatus.FORBIDDEN, "TERMINAL_NOT_OPERATIONAL",
                    "Terminal não está autorizado a operar");
        }
    }

    private Terminal credentialTerminalForCurrentTenant(String terminalId) {
        Terminal terminal = terminals.findByIdForCredentialUpdate(terminalId).orElseThrow(() ->
                new ApiBusinessException(HttpStatus.NOT_FOUND, "TERMINAL_NOT_FOUND", "Terminal não encontrado"));
        if (!terminal.getCondominio().getEmpresa().getId().equals(EmpresaContext.require())) {
            throw new ApiBusinessException(HttpStatus.NOT_FOUND, "TERMINAL_NOT_FOUND", "Terminal não encontrado");
        }
        return terminal;
    }

    private TerminalCredentialResponse issue(Terminal terminal) {
        byte[] random = new byte[CREDENTIAL_BYTES];
        SECURE_RANDOM.nextBytes(random);
        String raw = "tdc_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        int nextVersion = terminal.getDeviceCredentialVersion() == null ? 1 : terminal.getDeviceCredentialVersion() + 1;
        Instant now = Instant.now();
        terminal.setDeviceCredentialHash(hash(raw));
        terminal.setDeviceCredentialVersion(nextVersion);
        terminal.setDeviceCredentialCreatedAt(now);
        terminal.setDeviceCredentialRevokedAt(null);
        terminals.saveAndFlush(terminal);
        return new TerminalCredentialResponse(terminal.getIdTerminal(), raw, nextVersion, now);
    }

    private AuthenticatedTerminal principal(Terminal terminal) {
        return new AuthenticatedTerminal(terminal.getIdTerminal(),
                terminal.getCondominio().getEmpresa().getId(), terminal.getCondominio().getIdCondominio(),
                terminal.getDeviceCredentialVersion());
    }

    private String hash(String credential) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(credential.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponível", impossible);
        }
    }

    private ApiBusinessException unauthorized() {
        return new ApiBusinessException(HttpStatus.UNAUTHORIZED, "TERMINAL_AUTH_INVALID",
                "Credencial do Terminal inválida");
    }
}
