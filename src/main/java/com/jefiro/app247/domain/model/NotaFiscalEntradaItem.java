package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.NotaFiscalItemAcao;
import com.jefiro.app247.domain.model.enum_type.NotaFiscalItemStatus;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "nota_fiscal_entrada_item", uniqueConstraints =
        @UniqueConstraint(name = "uk_nota_fiscal_item_numero", columnNames = {"nota_id", "numero_item"}))
public class NotaFiscalEntradaItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false) private Empresa empresa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nota_id", nullable = false) private NotaFiscalEntrada nota;
    @Column(name = "numero_item", nullable = false) private int numeroItem;
    @Column(name = "codigo_fornecedor", nullable = false, length = 60) private String codigoFornecedor;
    @Column(length = 14) private String ean;
    @Column(nullable = false, length = 180) private String descricao;
    @Column(length = 8) private String ncm;
    @Column(length = 7) private String cest;
    @Column(length = 4) private String cfop;
    @Column(name = "unidade_comercial", nullable = false, length = 6) private String unidadeComercial;
    @Column(nullable = false, precision = 15, scale = 4) private BigDecimal quantidade;
    @Column(name = "valor_unitario", nullable = false, precision = 21, scale = 10) private BigDecimal valorUnitario;
    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2) private BigDecimal valorTotal;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private NotaFiscalItemAcao acao;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "produto_id") private Produto produto;
    @Column(name = "nome_produto", length = 180) private String nomeProduto;
    @Column(name = "preco_venda", precision = 15, scale = 6) private BigDecimal precoVenda;
    @Enumerated(EnumType.STRING) @Column(length = 50) private ProdutoCategoria categoria;
    @Enumerated(EnumType.STRING) @Column(name = "unidade_medida", nullable = false, length = 20)
    private UnidadeMedida unidadeMedida;
    @Column(name = "fator_conversao", nullable = false, precision = 15, scale = 3)
    private BigDecimal fatorConversao = BigDecimal.ONE.setScale(3);
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private NotaFiscalItemStatus status;
    @Column(length = 500) private String mensagem;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    /** Quantidade que entra no estoque, na unidade de venda (ex.: 2 CX × 12 = 24 UN). */
    public BigDecimal getQuantidadeEntrada() {
        return quantidade.multiply(fatorConversao).setScale(3, RoundingMode.HALF_UP);
    }

    /** Custo de uma unidade de venda, para comparar com o preço. */
    public BigDecimal getCustoUnitarioVenda() {
        BigDecimal entrada = getQuantidadeEntrada();
        if (entrada.signum() == 0) return BigDecimal.ZERO;
        return valorTotal.divide(entrada, 4, RoundingMode.HALF_UP);
    }

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
