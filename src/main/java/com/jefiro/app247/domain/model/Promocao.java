package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.AbrangenciaPromocao;
import com.jefiro.app247.domain.model.enum_type.StatusPromocao;
import com.jefiro.app247.domain.model.enum_type.TipoPromocao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promocao", indexes = {
        @Index(name = "idx_promocao_empresa_periodo", columnList = "empresa_id, ativo, inicio, fim"),
        @Index(name = "idx_promocao_condominio_periodo", columnList = "condominio_id, ativo, inicio, fim")
})
public class Promocao {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_promocao", length = 36)
    private String idPromocao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condominio_id")
    private Condominio condominio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AbrangenciaPromocao abrangencia;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(length = 500)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoPromocao tipo;

    @Column(nullable = false, precision = 15, scale = 6)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(nullable = false)
    private LocalDateTime fim;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false)
    private int prioridade;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "promocao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PromocaoProduto> produtos = new ArrayList<>();

    @PrePersist
    void prePersist() {
        LocalDateTime agora = LocalDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) createdAt = agora;
        updatedAt = agora;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public StatusPromocao statusEm(LocalDateTime agora) {
        if (!ativo) return StatusPromocao.DESATIVADA;
        if (agora.isBefore(inicio)) return StatusPromocao.AGENDADA;
        if (!agora.isBefore(fim)) return StatusPromocao.ENCERRADA;
        return StatusPromocao.ATIVA;
    }

    public void adicionarProduto(Produto produto) {
        PromocaoProduto associacao = new PromocaoProduto();
        associacao.setPromocao(this);
        associacao.setProduto(produto);
        produtos.add(associacao);
    }

    public List<Produto> produtosAssociados() {
        return produtos.stream().map(PromocaoProduto::getProduto).toList();
    }
}
