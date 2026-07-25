package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.dto.EmpresaRequest;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "empresa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String razaoSocial;

    @Column(nullable = false)
    private String nomeFantasia;

    @Column(nullable = false, unique = true, length = 18)
    private String cnpj;

    @Column(nullable = false, unique = true)
    private String email;

    private String telefone;

    private String cep;

    private String logradouro;

    private String numero;

    private String bairro;

    private String cidade;

    private String estado;

    @Column(nullable = false, unique = true)
    private String tenantId;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(nullable = false)
    private LocalDateTime dataCadastro;

    @PrePersist
    public void prePersist() {
        this.dataCadastro = LocalDateTime.now();

        if (this.tenantId == null) {
            this.tenantId = UUID.randomUUID().toString();
        }
    }

    public Empresa(EmpresaRequest empresa) {
        this.razaoSocial = empresa.razaoSocial();
        this.nomeFantasia = empresa.nomeFantasia();
        this.cnpj = empresa.cnpj();
        this.email = empresa.email();
        this.telefone = empresa.telefone();
        this.cep = empresa.cep();
        this.logradouro = empresa.logradouro();
        this.numero = empresa.numero();
        this.bairro = empresa.bairro();
        this.cidade = empresa.cidade();
        this.estado = empresa.estado();

        this.ativo = true;
        this.dataCadastro = LocalDateTime.now();
    }
}