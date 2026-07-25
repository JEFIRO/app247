CREATE TABLE terminal
(
    id_terminal     varchar(26) PRIMARY KEY,
    nome            VARCHAR(150),
    codigo          VARCHAR(80),
    serial_number   VARCHAR(120),
    mac_address     VARCHAR(50),
    ip_address      VARCHAR(45),
    ativo           BOOLEAN   DEFAULT TRUE,
    status          VARCHAR(30),
    last_ping       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    versao_software VARCHAR(50),
    id_endereco     BIGINT,
    id_condominio   varchar(36) NOT NULL,
    create_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    empresa_id      varchar(36) NOT NULL,
    CONSTRAINT fk_terminal_empresa
        FOREIGN KEY (empresa_id)
            REFERENCES empresa (id),

    CONSTRAINT fk_terminal_condominio
        FOREIGN KEY (id_condominio)
            REFERENCES condominio (id_condominio)
            ON DELETE CASCADE
);