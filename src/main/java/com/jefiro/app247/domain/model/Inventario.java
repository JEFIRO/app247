package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.InventarioStatus;
import com.jefiro.app247.domain.model.enum_type.TipoLocalEstoque;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "inventario")
public class Inventario {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false) private Empresa empresa;
    @Enumerated(EnumType.STRING) @Column(name = "local_tipo", nullable = false, length = 30)
    private TipoLocalEstoque localTipo;
    @Column(name = "local_id", nullable = false, columnDefinition = "char(36)", length = 36)
    private String localId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private InventarioStatus status = InventarioStatus.RASCUNHO;
    @Column(length = 180) private String descricao;
    @Column(length = 600) private String observacao;
    @Column(name = "contagem_cega", nullable = false) private boolean contagemCega;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by") private User createdBy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "finalized_by") private User finalizedBy;
    @Version @Column(name = "lock_version", nullable = false) private Long lockVersion;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finalized_at") private Instant finalizedAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @OneToMany(mappedBy = "inventario", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @OrderBy("createdAt ASC")
    private List<InventarioItem> itens = new ArrayList<>();

    @PrePersist void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = InventarioStatus.RASCUNHO;
    }
}
