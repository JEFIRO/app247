package com.jefiro.app247.domain.model;


import com.jefiro.app247.domain.model.dto.CreateProductDTO;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import com.jefiro.app247.infra.service.MoneyPolicy;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

@Entity
@Table(name = "produto", uniqueConstraints = @UniqueConstraint(
        name = "uk_produto_empresa_codigo", columnNames = {"empresa_id", "codigo"}))
public class Produto {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String idProduto;
    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_tributario")
    private GrupoTributario grupoTributario;
    @Column(nullable = false)
    private String codigo;
    private String nome;
    @Column(nullable = false, precision = 15, scale = 6)
    private BigDecimal preco;
    @Enumerated(EnumType.STRING)
    private UnidadeMedida unidadeMedida;
    @Enumerated(EnumType.STRING)
    private ProdutoCategoria categoria;
    private String descricao;
    private String foto;
    private BigDecimal peso;
    private BigDecimal pesoTolerancia;

    private LocalDateTime createAt;
    private LocalDateTime updateAt;
    private boolean status;

    @PrePersist
    private void prePersist() {
        LocalDateTime agora = LocalDateTime.now(ZoneOffset.UTC);
        if (createAt == null) createAt = agora;
        updateAt = agora;
    }

    @PreUpdate
    private void preUpdate() {
        updateAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Produto(CreateProductDTO produtoDTO) {
        this.codigo = produtoDTO.codigo();
        this.nome = produtoDTO.nome();
        this.preco = MoneyPolicy.persistence(produtoDTO.preco());
        this.unidadeMedida = UnidadeMedida.valueOf(produtoDTO.unidadeMedida().toUpperCase());
        this.categoria = ProdutoCategoria.valueOf(produtoDTO.categoria().toUpperCase());
        this.descricao = produtoDTO.descricao();
        this.foto = produtoDTO.foto();
        this.peso = produtoDTO.peso();
        this.pesoTolerancia = produtoDTO.pesoTolerancia();
        this.createAt = LocalDateTime.now(ZoneOffset.UTC);
        this.updateAt = this.createAt;
        this.status = true;
    }
}
