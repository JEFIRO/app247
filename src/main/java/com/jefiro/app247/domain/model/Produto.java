package com.jefiro.app247.domain.model;


import com.jefiro.app247.domain.model.dto.CreateProductDTO;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

@Entity
@Table(name = "produto")
public class Produto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String codigo;
    private String nome;
    private double preco;
    private int quantidade;
    private String unidadeMedida;
    private String categoria;
    private String descricao;
    private String foto;
    private double peso;
    private double pesoTolerancia;
    private LocalDateTime create_at;
    private LocalDateTime update_at;
    private boolean status;

    public Produto(CreateProductDTO produtoDTO) {
        this.codigo = produtoDTO.codigo();
        this.nome = produtoDTO.nome();
        this.preco = produtoDTO.preco();
        this.quantidade = produtoDTO.quantidade();
        this.unidadeMedida = produtoDTO.unidadeMedida();
        this.categoria = produtoDTO.categoria();
        this.descricao = produtoDTO.descricao();
        this.foto = produtoDTO.foto();
        this.peso = produtoDTO.peso();
        this.pesoTolerancia = produtoDTO.pesoTolerancia();
    }
}
