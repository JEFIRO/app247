package com.jefiro.app247.infra.exception;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RestExceptionHandlerMessageTest {

    @Test
    void mensagemDeRegraDoAppSegueParaOCliente() {
        IllegalArgumentException ex = new IllegalArgumentException("Entrada deve ser positiva");

        assertThat(RestExceptionHandler.safeMessage(ex, "genérica")).isEqualTo("Entrada deve ser positiva");
    }

    @Test
    void mensagemDeBibliotecaViraGenerica() {
        IllegalArgumentException uuid = capture(() -> UUID.fromString("nao-e-uuid"));
        NoSuchElementException optional = capture(() -> Optional.empty().orElseThrow());

        assertThat(RestExceptionHandler.safeMessage(uuid, "Dados inválidos.")).isEqualTo("Dados inválidos.");
        assertThat(RestExceptionHandler.safeMessage(optional, "Recurso não encontrado."))
                .isEqualTo("Recurso não encontrado.");
    }

    @SuppressWarnings("unchecked")
    private static <T extends RuntimeException> T capture(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            return (T) ex;
        }
        throw new AssertionError("esperava exceção");
    }
}
