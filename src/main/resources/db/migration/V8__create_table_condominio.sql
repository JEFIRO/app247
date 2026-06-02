CREATE TABLE condominio
(
    condominio_id   INTEGER PRIMARY KEY AUTO_INCREMENT,
    uuid_condominio varchar(36)  not null unique,
    nome            VARCHAR(150) NOT NULL,
    cnpj            VARCHAR(20) UNIQUE,
    endereco_id        integer,
    ativo           BOOLEAN   DEFAULT TRUE,

    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);