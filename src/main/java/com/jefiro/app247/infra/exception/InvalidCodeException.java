package com.jefiro.app247.infra.exception;

public class InvalidCodeException extends RuntimeException {
      public InvalidCodeException() {
        super("Código inválido");
    }
    public InvalidCodeException(String message) {
        super(message);
    }
}
