package com.jefiro.app247.infra.exception;

public class DuplicateCpfException extends RuntimeException {
     public DuplicateCpfException() {
        super("Já existe um usuário com esse CPF");
    }
    public DuplicateCpfException(String message) {
        super(message);
    }
}
