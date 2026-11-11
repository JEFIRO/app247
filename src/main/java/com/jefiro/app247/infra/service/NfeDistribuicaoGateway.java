package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;

import java.util.Optional;

/**
 * Busca o XML da NF-e na SEFAZ pela chave (NFeDistribuicaoDFe, consChNFe). Exige o certificado
 * A1 do CNPJ destinatário da nota, então só funciona para Empresas que cadastraram o seu.
 */
public interface NfeDistribuicaoGateway {
    boolean disponivel(Empresa empresa);

    Optional<byte[]> baixarXml(Empresa empresa, String chave);
}
