package com.jefiro.app247.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "produto_fiscal", uniqueConstraints =
        @UniqueConstraint(name = "uk_produto_fiscal_produto", columnNames = "produto_id"))
public class ProdutoFiscal {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false) private Empresa empresa;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false, unique = true) private Produto produto;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "perfil_tributario_id") private PerfilTributario perfilTributario;
    @Column(length = 8) private String ncm;
    @Column(length = 7) private String cest;
    @Column(name = "origem_mercadoria", length = 2) private String origemMercadoria;
    @Column(name = "unidade_tributavel", length = 6) private String unidadeTributavel;
    @Column(name = "fator_conversao_tributavel", precision = 15, scale = 6) private BigDecimal fatorConversaoTributavel;
    @Column(name = "gtin_tributavel", length = 14) private String gtinTributavel;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @PrePersist void prePersist(){Instant now=Instant.now();if(createdAt==null)createdAt=now;updatedAt=now;if(empresa==null&&produto!=null)empresa=produto.getEmpresa();}
    @PreUpdate void preUpdate(){updatedAt=Instant.now();}
}
