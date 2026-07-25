CREATE TABLE grupo_tributario
(
    id_tributacao   varchar(25) primary key,
    descricao       VARCHAR(100),

    ncm             VARCHAR(10),
    cest            VARCHAR(10),
    cfop            VARCHAR(10),
    cst             VARCHAR(5),
    csosn           VARCHAR(5),

    aliquota_icms   DECIMAL(5, 2),
    aliquota_pis    DECIMAL(5, 2),
    aliquota_cofins DECIMAL(5, 2),
    aliquota_ipi    DECIMAL(5, 2),

    empresa_id      varchar(36) NOT NULL,
    CONSTRAINT fk_grupo_tributario_empresa
        FOREIGN KEY (empresa_id)
            REFERENCES empresa (id)
);