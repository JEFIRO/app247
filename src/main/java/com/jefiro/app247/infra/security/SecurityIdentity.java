package com.jefiro.app247.infra.security;

import com.jefiro.app247.domain.model.auth.User;

import java.time.Instant;

/**
 * Dados estritamente necessários para autenticar uma requisição sem navegar
 * pelos relacionamentos LAZY do usuário.
 */
public record SecurityIdentity(
        User user,
        String empresaId,
        Boolean usuarioAtivo,
        Boolean empresaAtiva,
        Instant empresaEncerradaEm
) {
}
