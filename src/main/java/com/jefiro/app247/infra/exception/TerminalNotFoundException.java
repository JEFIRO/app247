package com.jefiro.app247.infra.exception;

public class TerminalNotFoundException extends RuntimeException {
    public TerminalNotFoundException() {
        super("Terminal não encontrado");
    }

    public TerminalNotFoundException(String message) {
        super(message);
    }
}
