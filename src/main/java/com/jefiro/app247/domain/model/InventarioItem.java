package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.InventarioItemStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "inventario_item", uniqueConstraints =
        @UniqueConstraint(name = "uk_inventario_item_produto", columnNames = {"inventario_id", "produto_id"}))
public class InventarioItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false) private Empresa empresa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventario_id", nullable = false) private Inventario inventario;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false) private Produto produto;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "estoque_empresa_id")
    private EstoqueEmpresa estoqueEmpresa;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "estoque_condominio_id")
    private EstoqueCondominio estoqueCondominio;
    @Column(name = "saldo_sistema_snapshot", nullable = false, precision = 15, scale = 3)
    private BigDecimal saldoSistemaSnapshot;
    @Column(name = "estoque_version_snapshot", nullable = false)
    private Long estoqueVersionSnapshot;
    @Column(name = "estoque_updated_at_snapshot", nullable = false)
    private Instant estoqueUpdatedAtSnapshot;
    @Column(name = "quantidade_contada", precision = 15, scale = 3)
    private BigDecimal quantidadeContada;
    @Column(precision = 15, scale = 3) private BigDecimal diferenca;
    @Column(name = "saldo_atual_conflito", precision = 15, scale = 3)
    private BigDecimal saldoAtualConflito;
    @Column(name = "motivo_ajuste", length = 300) private String motivoAjuste;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "contado_por") private User contadoPor;
    @Column(name = "contado_em") private Instant contadoEm;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private InventarioItemStatus status = InventarioItemStatus.PENDENTE;
    @Column(name = "ajuste_aplicado", nullable = false) private boolean ajusteAplicado;
    @Column(name = "ajuste_aplicado_em") private Instant ajusteAplicadoEm;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = InventarioItemStatus.PENDENTE;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
