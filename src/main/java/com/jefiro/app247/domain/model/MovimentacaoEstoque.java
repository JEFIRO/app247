package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.TipoMovimentacaoEstoque;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "movimentacao_estoque")
public class MovimentacaoEstoque {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estoque_id", nullable = false)
    private EstoqueCondominio estoque;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimentacaoEstoque tipo;
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantidade;
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantidadeAnterior;
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantidadePosterior;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;
    private String motivo;
    @Column(nullable = false, unique = true, length = 160)
    private String chaveIdempotencia;
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
