package com.jefiro.app247.infra.exception;

public class ExpiredCodeException extends RuntimeException {
    public ExpiredCodeException(String message) {
        super(message);
    }
      public ExpiredCodeException() {
        super("Código expirado");
    }
}
