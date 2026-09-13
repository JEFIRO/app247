package com.jefiro.app247.domain.model.dto.estoque;

import com.jefiro.app247.domain.model.Planograma;
import com.jefiro.app247.domain.model.PlanogramaPosicao;
import com.jefiro.app247.domain.model.PlanogramaProduto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PlanogramaResponse(String id, String nome, String descricao, boolean ativo,
                                 Instant createdAt, Instant updatedAt, List<Posicao> posicoes) {
    public record Posicao(String id, String setor, String corredor, String estante, String modulo,
                          String prateleira, String posicao, int ordem, BigDecimal x, BigDecimal y,
                          BigDecimal largura, BigDecimal altura, String localizacao, List<Produto> produtos) {}
    public record Produto(String id, String produtoId, String nome, int facings, BigDecimal capacidade,
                          BigDecimal quantidadeIdeal, BigDecimal quantidadeMinima, boolean ativo) {}

    public static PlanogramaResponse from(Planograma planograma, List<PlanogramaPosicao> posicoes,
                                          Map<String, List<PlanogramaProduto>> produtos) {
        return new PlanogramaResponse(planograma.getId(), planograma.getNome(), planograma.getDescricao(),
                Boolean.TRUE.equals(planograma.getAtivo()), planograma.getCreatedAt(), planograma.getUpdatedAt(),
                posicoes.stream().map(pos -> new Posicao(pos.getId(), pos.getSetor(), pos.getCorredor(),
                        pos.getEstante(), pos.getModulo(), pos.getPrateleira(), pos.getPosicao(), pos.getOrdem(),
                        pos.getX(), pos.getY(), pos.getLargura(), pos.getAltura(), localizacao(pos),
                        produtos.getOrDefault(pos.getId(), List.of()).stream().map(p -> new Produto(p.getId(),
                                p.getProduto().getIdProduto(), p.getProduto().getNome(), p.getFacings(),
                                p.getCapacidade(), p.getQuantidadeIdeal(), p.getQuantidadeMinima(),
                                Boolean.TRUE.equals(p.getAtivo()))).toList())).toList());
    }

    public static String localizacao(PlanogramaPosicao posicao) {
        return java.util.stream.Stream.of(posicao.getCorredor(), posicao.getEstante(),
                        posicao.getPrateleira(), posicao.getPosicao())
                .filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.joining("-"));
    }
}
