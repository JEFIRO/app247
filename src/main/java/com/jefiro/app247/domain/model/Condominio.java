package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.auth.Endereco;
import com.jefiro.app247.domain.model.dto.CondominioRequest;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor

@Entity
@Table(name = "condominio")
public class Condominio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String idCondominio;

    private String nome;
    private String cnpj;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "id_endereco")
    private Endereco endereco;

    private Boolean ativo;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Condominio(CondominioRequest request, Endereco endereco) {
        this.nome = request.nome();
        this.cnpj = request.cnpj();
        this.endereco = endereco;
        this.ativo = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

}
