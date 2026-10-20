package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.dto.TerminalActivationResponse;
import com.jefiro.app247.domain.model.dto.TerminalCredentialResponse;
import com.jefiro.app247.domain.model.dto.TerminalPairingClaimResponse;
import com.jefiro.app247.domain.model.dto.TerminalPairingStartRequest;
import com.jefiro.app247.domain.model.dto.TerminalPairingStartResponse;
import com.jefiro.app247.domain.model.dto.TerminalResponseDTO;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.TerminalLifecycleState;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Pareamento do Terminal sem comando no equipamento:
 * <ol>
 *   <li>o Terminal sem credencial pede um código ({@link #start}) e guarda o pairingToken;</li>
 *   <li>o gestor digita ou escaneia o código no app admin ({@link #approve}), o que grava
 *       serial/MAC/IP no Terminal escolhido e libera a ativação;</li>
 *   <li>o Terminal troca o pairingToken pela credencial ({@link #claim}), uma única vez.</li>
 * </ol>
 * A credencial é gerada só no claim, então o texto dela nunca fica armazenado. Ver o código
 * na tela não basta para obtê-la: o claim exige o pairingToken, que não sai do equipamento.
 */
@Service
public class TerminalPairingService {
    private static final Logger log = LoggerFactory.getLogger(TerminalPairingService.class);
    /** Sem 0/O e 1/I/L, que se confundem na tela e na digitação. */
    private static final char[] CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 8;
    private static final int TOKEN_BYTES = 32;
    private static final String QR_PREFIX = "APP247-PAIR:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final java.util.List<PagamentoStatus> UNRESOLVED_PAYMENTS = Arrays.stream(PagamentoStatus.values())
            .filter(PagamentoStatus::isActive).toList();

    private final TerminalPairingStore store;
    private final TerminalRepository terminals;
    private final PagamentoRepository pagamentos;
    private final TerminalRequestAuthorizationService authorization;
    private final PublicAuthRateLimits limits;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transaction;
    private final Duration ttl;

    public TerminalPairingService(TerminalPairingStore store, TerminalRepository terminals,
                                  PagamentoRepository pagamentos,
                                  TerminalRequestAuthorizationService authorization,
                                  PublicAuthRateLimits limits, AuditLogService auditLogService,
                                  PlatformTransactionManager transactionManager,
                                  @Value("${app.terminal.pairing.ttl:PT15M}") Duration ttl) {
        this.store = store;
        this.terminals = terminals;
        this.pagamentos = pagamentos;
        this.authorization = authorization;
        this.limits = limits;
        this.auditLogService = auditLogService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.ttl = ttl;
    }

    public TerminalPairingStartResponse start(TerminalPairingStartRequest request, String origin) {
        limits.beforeTerminalPairing(origin);
        String token = "tpt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes());
        String sessionId = hash(token);
        try {
            for (int attempt = 0; attempt < 5; attempt++) {
                String code = newCode();
                TerminalPairingStore.Session session = new TerminalPairingStore.Session(sessionId, code,
                        request.serialNumber().trim(), blankToNull(request.macAddress()),
                        blankToNull(request.ipAddress()), null);
                if (store.create(session, ttl)) {
                    return new TerminalPairingStartResponse(token, display(code), QR_PREFIX + display(code),
                            Instant.now().plus(ttl));
                }
            }
        } catch (RuntimeException unavailable) {
            log.warn("[TERMINAL-PAIRING] armazenamento indisponível ao iniciar", unavailable);
            throw unavailable();
        }
        throw unavailable();
    }

    /** Gestor da Empresa confirma o código mostrado pelo Terminal. */
    public TerminalResponseDTO approve(String terminalId, String rawCode) {
        String empresaId = EmpresaContext.require();
        limits.beforeTerminalPairingApproval(empresaId);
        String code = normalizeCode(rawCode);
        TerminalPairingStore.Session session;
        try {
            session = code == null ? null : store.sessionIdForCode(code).flatMap(store::find)
                    .filter(found -> code.equals(found.code()) && !found.approved())
                    .orElse(null);
        } catch (RuntimeException unavailable) {
            log.warn("[TERMINAL-PAIRING] armazenamento indisponível ao aprovar", unavailable);
            throw unavailable();
        }
        if (session == null) {
            limits.terminalPairingApprovalFailed(empresaId);
            throw new ApiBusinessException(HttpStatus.NOT_FOUND, "PAIRING_CODE_INVALID",
                    "Código de pareamento inválido ou expirado. Confira a tela do Terminal.");
        }
        TerminalPairingStore.Session approved = session;
        TerminalResponseDTO response = transaction.execute(status -> bind(terminalId, empresaId, approved));
        if (!store.approve(session, terminalId)) {
            throw new ApiBusinessException(HttpStatus.NOT_FOUND, "PAIRING_CODE_INVALID",
                    "O código expirou. Use o novo código mostrado no Terminal.");
        }
        return response;
    }

    /** O Terminal troca o pairingToken pela credencial depois da aprovação. */
    public TerminalPairingClaimResponse claim(String pairingToken) {
        String sessionId = hash(pairingToken);
        TerminalPairingStore.Session session;
        try {
            session = store.find(sessionId).orElseThrow(TerminalPairingService::expired);
            if (!session.approved()) return TerminalPairingClaimResponse.pending();
            // Só quem remove a sessão recebe a credencial: requisições repetidas não geram duas.
            if (!store.consume(sessionId)) throw expired();
        } catch (ApiBusinessException business) {
            throw business;
        } catch (RuntimeException unavailable) {
            log.warn("[TERMINAL-PAIRING] armazenamento indisponível no claim", unavailable);
            throw unavailable();
        }
        TerminalPairingStore.Session paired = session;
        return transaction.execute(status -> {
            TerminalCredentialResponse credential =
                    authorization.issueForPairing(paired.terminalId(), paired.serialNumber());
            Terminal terminal = terminals.findById(paired.terminalId()).orElseThrow();
            auditLogService.record(terminal.getCondominio().getEmpresa(), "TERMINAL_PAIRED", "Terminal",
                    terminal.getIdTerminal(), null,
                    Map.of("serialNumber", terminal.getSerialNumber(), "credentialVersion", credential.version()),
                    Map.of());
            return TerminalPairingClaimResponse.paired(new TerminalActivationResponse(terminal), credential);
        });
    }

    private TerminalResponseDTO bind(String terminalId, String empresaId, TerminalPairingStore.Session session) {
        Terminal terminal = terminals.findByIdForCredentialUpdate(terminalId)
                .filter(found -> found.getCondominio().getEmpresa().getId().equals(empresaId))
                .orElseThrow(() -> new ApiBusinessException(HttpStatus.NOT_FOUND, "TERMINAL_NOT_FOUND",
                        "Terminal não encontrado"));
        if (!Boolean.TRUE.equals(terminal.getAtivo())
                || terminal.getLifecycleState() != TerminalLifecycleState.ACTIVE) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "TERMINAL_NOT_ACTIVE",
                    "Terminal desativado ou encerrado não pode ser pareado");
        }
        Optional<Terminal> sameSerial = terminals.findBySerialNumber(session.serialNumber());
        if (sameSerial.isPresent() && !sameSerial.get().getIdTerminal().equals(terminalId)) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "TERMINAL_SERIAL_IN_USE",
                    "Este equipamento já está vinculado a outro Terminal");
        }
        if (pagamentos.existsByOrder_Terminal_IdTerminalAndStatusIn(terminalId, UNRESOLVED_PAYMENTS)) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "TERMINAL_ACTIVE_PAYMENTS",
                    "O Terminal tem pagamento em andamento; aguarde a conclusão para parear");
        }
        String serialAnterior = terminal.getSerialNumber();
        terminal.setSerialNumber(session.serialNumber());
        if (session.macAddress() != null) terminal.setMacAddress(session.macAddress());
        if (session.ipAddress() != null) terminal.setIpAddress(session.ipAddress());
        terminal.setUpdate_at(Instant.now());
        Terminal saved = terminals.saveAndFlush(terminal);
        auditLogService.record(saved.getCondominio().getEmpresa(), "TERMINAL_PAIRING_APPROVED", "Terminal",
                terminalId, serialAnterior == null ? null : Map.of("serialNumber", serialAnterior),
                Map.of("serialNumber", saved.getSerialNumber()), Map.of());
        return new TerminalResponseDTO(saved);
    }

    /** Aceita "K7Q4-MZ8P", "k7q4mz8p" ou o conteúdo inteiro do QR. */
    static String normalizeCode(String raw) {
        if (raw == null) return null;
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.startsWith(QR_PREFIX)) value = value.substring(QR_PREFIX.length());
        value = value.replaceAll("[^A-Z0-9]", "");
        return value.length() == CODE_LENGTH ? value : null;
    }

    private static String display(String code) {
        return code.substring(0, 4) + "-" + code.substring(4);
    }

    private static String newCode() {
        char[] code = new char[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) code[i] = CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)];
        return new String(code);
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String hash(String token) {
        if (token == null || token.isBlank()) throw expired();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponível", impossible);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiBusinessException expired() {
        return new ApiBusinessException(HttpStatus.GONE, "PAIRING_EXPIRED",
                "Sessão de pareamento expirada; gere um novo código");
    }

    private static ApiBusinessException unavailable() {
        return new ApiBusinessException(HttpStatus.SERVICE_UNAVAILABLE, "PAIRING_UNAVAILABLE",
                "Pareamento indisponível no momento; tente novamente");
    }
}
