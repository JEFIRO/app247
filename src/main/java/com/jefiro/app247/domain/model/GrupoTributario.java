package com.jefiro.app247.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "grupo_tributario")

@Getter
@Setter
public class GrupoTributario {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id_tributacao;
    private String descricao;
    private String ncm;
    private String cest;
    private String cfop;
    private String cst;
    private String csosn;

    @Column(precision = 5, scale = 2)
    private BigDecimal aliquotaIcms;
    @Column(precision = 5, scale = 2)
    private BigDecimal aliquotaPis;
    @Column(name = "aliquota_cofins", precision = 5, scale = 2)
    private BigDecimal aliquotaConfins;
    @Column(precision = 5, scale = 2)
    private BigDecimal aliquotaIpi;
    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

}
