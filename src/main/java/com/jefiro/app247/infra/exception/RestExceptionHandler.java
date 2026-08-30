package com.jefiro.app247.infra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.util.NoSuchElementException;
import com.jefiro.app247.domain.model.dto.PriceChangedResponse;

@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(PriceChangedException.class)
    public ResponseEntity<PriceChangedResponse> priceChanged(PriceChangedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(PriceChangedResponse.from(exception));
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
        return ResponseEntity.status(status).body(new RestErrorMessage(status, exception.getReason()));
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
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new RestErrorMessage(HttpStatus.CONFLICT, ex.getMessage()));
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
        return ResponseEntity.badRequest().body(new RestErrorMessage(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<RestErrorMessage> fileStorage(FileStorageException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new RestErrorMessage(
                HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível armazenar o arquivo"));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<RestErrorMessage> notFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new RestErrorMessage(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<RestErrorMessage> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new RestErrorMessage(HttpStatus.CONFLICT, ex.getMessage()));
    }
}
