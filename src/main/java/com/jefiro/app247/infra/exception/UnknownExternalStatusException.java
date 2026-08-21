package com.jefiro.app247.infra.exception;

public class UnknownExternalStatusException extends RuntimeException {
    public UnknownExternalStatusException(String message) {
        super(message);
    }
}
