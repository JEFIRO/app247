package com.jefiro.app247.infra.service;

public class EmpresaContext {

    private static final ThreadLocal<String> EMPRESA =
        new ThreadLocal<>();

    public static void set(String empresa) {
        EMPRESA.set(empresa);
    }

    public static String get() {
        return EMPRESA.get();
    }

    public static void clear() {
        EMPRESA.remove();
    }
}