package com.jefiro.app247.infra.exception;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("Token inválido");
    }

    public InvalidTokenException(String message) {
        super(message);
    }
}
