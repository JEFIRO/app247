CREATE TABLE terminal
(
    terminal_id     INTEGER AUTO_INCREMENT PRIMARY KEY,
    uuid_terminal   VARCHAR(100),

    nome            VARCHAR(150),
    codigo          VARCHAR(80),
    serial_number   VARCHAR(120),
    mac_address     VARCHAR(50),
    ip_address      VARCHAR(45),

    ativo           BOOLEAN   DEFAULT TRUE,
    status          VARCHAR(30),

    last_ping       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    versao_software VARCHAR(50),

    endereco_id     BIGINT,
    condominio_id   integer NOT NULL,
    create_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_terminal_condominio
        FOREIGN KEY (condominio_id)
            REFERENCES condominio (condominio_id)
            ON DELETE CASCADE
);