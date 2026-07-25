package com.jefiro.app247.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    private Double aliquotaIcms;
    private Double aliquotaPis;
    private Double aliquotaConfins;
    private Double aliquotaIpi;
    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

}
