package com.jefiro.app247.domain.model.auth;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.dto.EnderecoDTO;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "endereco")
public class Endereco {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String idEndereco;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;
    @Column(nullable = false, length = 180)
    private String rua;
    @Column(nullable = false, length = 20)
    private String numero;
    @Column(length = 100)
    private String complemento;
    @Column(nullable = false, length = 100)
    private String bairro;
    @Column(nullable = false, length = 100)
    private String cidade;
    @Column(nullable = false, length = 2)
    private String estado;
    @Column(nullable = false, length = 9)
    private String cep;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Endereco(EnderecoDTO dto) {
        rua = dto.rua();
        numero = dto.numero();
        complemento = dto.complemento();
        bairro = dto.bairro();
        cidade = dto.cidade();
        estado = dto.estado();
        cep = dto.cep();
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
