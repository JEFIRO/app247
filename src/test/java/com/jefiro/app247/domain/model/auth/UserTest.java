package com.jefiro.app247.domain.model.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void usuarioInativoNaoEstaHabilitadoParaAutenticacao() {
        User user = new User();
        user.setAtivo(false);

        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    void usuarioAtivoEstaHabilitadoParaAutenticacao() {
        User user = new User();
        user.setAtivo(true);

        assertThat(user.isEnabled()).isTrue();
    }
}
