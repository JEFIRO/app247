CREATE TABLE condominio
(
    id_condominio varchar(36) PRIMARY KEY,
    nome          VARCHAR(150) NOT NULL,
    cnpj          VARCHAR(20) UNIQUE,
    id_endereco   varchar(36),
    ativo         BOOLEAN   DEFAULT TRUE,

    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    empresa_id    varchar(36)  NOT NULL,
    CONSTRAINT fk_condominio_empresa
        FOREIGN KEY (empresa_id)
            REFERENCES empresa (id)
);