package com.jefiro.app247.infra.service.comprovante;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Bloco opcional do contrato Python; versões antigas do serviço o ignoram. */
public record ComprovanteDetalhesRequest(
        String subtotal,
        List<DescontoComprovanteRequest> descontos,
        String arredondamento,
        String bandeira,
        Integer parcelas,
        @JsonProperty("transacao_id") String transacaoId,
        String nsu,
        String autorizacao,
        @JsonProperty("cliente_vinculado") boolean clienteVinculado,
        @JsonProperty("cliente_nome") String clienteNome,
        @JsonProperty("pontos_ganhos") Long pontosGanhos,
        @JsonProperty("cashback_ganho") String cashbackGanho,
        String contato,
        @JsonProperty("logo_url") String logoUrl
) {
}
