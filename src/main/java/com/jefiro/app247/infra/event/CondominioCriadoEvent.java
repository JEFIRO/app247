package com.jefiro.app247.infra.event;

import com.jefiro.app247.domain.model.Condominio;

/** Publicado na transação que cria o Condomínio, antes do commit. */
public record CondominioCriadoEvent(Condominio condominio) {}
