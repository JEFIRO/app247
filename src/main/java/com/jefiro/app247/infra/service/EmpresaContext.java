package com.jefiro.app247.infra.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class EmpresaContext {

    private static final ThreadLocal<String> EMPRESA = new ThreadLocal<>();

    public static void set(String empresa) {
        EMPRESA.set(empresa);
    }

    public static String get() {
        return EMPRESA.get();
    }

    public static String require() {
        String empresaId = EMPRESA.get();
        if (empresaId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Empresa não identificada na autenticação");
        }
        return empresaId;
    }

    public static void clear() {
        EMPRESA.remove();
    }
}
