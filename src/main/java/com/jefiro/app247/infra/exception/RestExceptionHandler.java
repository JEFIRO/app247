package com.jefiro.app247.infra.exception;

import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.util.NoSuchElementException;
import com.jefiro.app247.domain.model.dto.PriceChangedResponse;
import com.jefiro.app247.domain.model.dto.PaymentAlreadyActiveResponse;
import org.springframework.web.context.request.WebRequest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.LinkedHashMap;
import com.jefiro.app247.infra.service.OrderPriceFinalizationService;
import com.jefiro.app247.domain.model.dto.appclient.AppClienteContracts.CouponMinimumError;

@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(OrderPriceFinalizationService.CouponMinimumException.class)
    public ResponseEntity<CouponMinimumError> couponMinimum(OrderPriceFinalizationService.CouponMinimumException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new CouponMinimumError(
                "COUPON_MINIMUM_NOT_REACHED", ex.minimum(), ex.current(), ex.minimum().subtract(ex.current())));
    }
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        var fields = new LinkedHashMap<String, String>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        RestErrorMessage body = new RestErrorMessage(
                HttpStatus.BAD_REQUEST,
                "Verifique os campos informados.",
                request.getDescription(false).contains("/auth/register")
                        ? "INVALID_REGISTRATION_DATA" : "VALIDATION_ERROR",
                fields
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(PriceChangedException.class)
    public ResponseEntity<PriceChangedResponse> priceChanged(PriceChangedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(PriceChangedResponse.from(exception));
    }

    @ExceptionHandler(PaymentAlreadyActiveException.class)
    public ResponseEntity<PaymentAlreadyActiveResponse> paymentAlreadyActive(
            PaymentAlreadyActiveException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(PaymentAlreadyActiveResponse.from(exception));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<RestErrorMessage> userNotFound(UserNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new RestErrorMessage(HttpStatus.NOT_FOUND, exception.getMessage()));
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<RestErrorMessage> invalidPassword(InvalidPasswordException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new RestErrorMessage(HttpStatus.UNAUTHORIZED, exception.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<RestErrorMessage> noAtoties(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String code = exception instanceof ApiBusinessException business ? business.getCode() : null;
        return ResponseEntity.status(status).body(
                new RestErrorMessage(status, exception.getReason(), code, null));
    }

    @ExceptionHandler(ExpiredCodeException.class)
    public ResponseEntity<RestErrorMessage> noAtoties(ExpiredCodeException exception) {
        return ResponseEntity.status(HttpStatus.GONE).body(new RestErrorMessage(HttpStatus.GONE, exception.getMessage()));
    }

    @ExceptionHandler(InvalidCodeException.class)
    public ResponseEntity<RestErrorMessage> noAtoties(InvalidCodeException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new RestErrorMessage(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler(ExpiredTokenException.class)
    public ResponseEntity<RestErrorMessage> expiredToken(ExpiredTokenException ex) {
        return ResponseEntity.status(HttpStatus.GONE).body(new RestErrorMessage(HttpStatus.GONE, ex.getMessage()));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<RestErrorMessage> invalidToken(InvalidTokenException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new RestErrorMessage(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(DuplicateCpfException.class)
    public ResponseEntity<RestErrorMessage> duplicateCpf(DuplicateCpfException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new RestErrorMessage(
                HttpStatus.CONFLICT, "Já existe uma conta cadastrada com este CPF.",
                "CPF_ALREADY_REGISTERED", null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<RestErrorMessage> dataConflict(DataIntegrityViolationException ex) {
        String detail = rootMessage(ex).toLowerCase(java.util.Locale.ROOT);
        if (detail.contains("uk_users_cpf") || detail.contains("users.cpf")) {
            return conflict("CPF_ALREADY_REGISTERED", "Já existe uma conta cadastrada com este CPF.");
        }
        if (detail.contains("uk_users_email") || detail.contains("users.email")) {
            return conflict("EMAIL_ALREADY_REGISTERED", "Este e-mail já está sendo utilizado.");
        }
        return conflict("DATA_CONFLICT", "Os dados informados conflitam com um cadastro existente.");
    }
    @ExceptionHandler(TerminalNotFoundException.class)
    public ResponseEntity<RestErrorMessage> TerminalNotFound(TerminalNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new RestErrorMessage(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<RestErrorMessage> externalService(ExternalServiceException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new RestErrorMessage(
                HttpStatus.BAD_GATEWAY,
                "Falha temporária na integração com " + ex.getService()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RestErrorMessage> invalidArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new RestErrorMessage(HttpStatus.BAD_REQUEST,
                safeMessage(ex, "Dados inválidos.")));
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<RestErrorMessage> fileStorage(FileStorageException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new RestErrorMessage(
                HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível armazenar o arquivo"));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<RestErrorMessage> notFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new RestErrorMessage(HttpStatus.NOT_FOUND, safeMessage(ex, "Recurso não encontrado.")));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<RestErrorMessage> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new RestErrorMessage(HttpStatus.CONFLICT, safeMessage(ex, "Operação não permitida no estado atual.")));
    }

    /**
     * Mensagens das regras do App 24/7 são escritas para o usuário e seguem na resposta.
     * Exceções lançadas por bibliotecas (JDK, Spring, Hibernate) podem expor detalhes
     * internos, então viram uma mensagem genérica e o texto original vai só para o log.
     */
    static String safeMessage(RuntimeException ex, String fallback) {
        StackTraceElement[] trace = ex.getStackTrace();
        boolean fromApp = trace.length > 0 && trace[0].getClassName().startsWith("com.jefiro.app247.");
        if (fromApp && ex.getMessage() != null && !ex.getMessage().isBlank()) return ex.getMessage();
        if (!fromApp) {
            LoggerFactory.getLogger(RestExceptionHandler.class).warn("[HTTP-ERROR] {} ocultada: {}",
                    ex.getClass().getSimpleName(), ex.getMessage());
        }
        return fallback;
    }

    private ResponseEntity<RestErrorMessage> conflict(String code, String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new RestErrorMessage(HttpStatus.CONFLICT, message, code, null));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "" : current.getMessage();
    }
}
