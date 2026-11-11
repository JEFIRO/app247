package com.jefiro.app247.domain.model.dto.notafiscal;

/**
 * O que dá para saber só pela chave do DANFE. Os itens só vêm do XML: por upload ou,
 * quando a Empresa tiver certificado A1, baixado da SEFAZ.
 */
public record ChaveAcessoResponse(
        String chave,
        String uf,
        String anoMes,
        String emitenteCnpj,
        String modelo,
        String serie,
        String numero,
        String notaId,
        String status,
        boolean sefazDisponivel,
        String mensagem
) {}
