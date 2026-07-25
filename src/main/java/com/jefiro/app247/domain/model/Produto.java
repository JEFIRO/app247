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

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

@Entity
@Table(name = "produto")
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
    @Column(unique = true)
    private String codigo;
    private String nome;
    private BigDecimal preco;
    private Integer quantidade;
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

    public Produto(CreateProductDTO produtoDTO) {
        this.codigo = produtoDTO.codigo();
        this.nome = produtoDTO.nome();
        this.preco = produtoDTO.preco();
        this.quantidade = produtoDTO.quantidade();
        this.unidadeMedida = UnidadeMedida.valueOf(produtoDTO.unidadeMedida().toUpperCase());
        this.categoria = ProdutoCategoria.valueOf(produtoDTO.categoria().toUpperCase());
        this.descricao = produtoDTO.descricao();
        this.foto = produtoDTO.foto();
        this.peso = produtoDTO.peso();
        this.pesoTolerancia = produtoDTO.pesoTolerancia();
        this.createAt = LocalDateTime.now();
        this.updateAt = LocalDateTime.now();
        this.status = true;
    }
}
