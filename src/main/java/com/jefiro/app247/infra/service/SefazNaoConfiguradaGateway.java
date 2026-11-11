package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Enquanto não houver integração com certificado A1, a nota entra pelo upload do XML. */
@Component
public class SefazNaoConfiguradaGateway implements NfeDistribuicaoGateway {
    @Override public boolean disponivel(Empresa empresa) { return false; }

    @Override public Optional<byte[]> baixarXml(Empresa empresa, String chave) { return Optional.empty(); }
}
