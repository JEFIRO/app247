CREATE TABLE mercado_pago_conta
(
    id_mercado_conta VARCHAR(36)  NOT NULL primary key,
    empresa_id       VARCHAR(36)  NOT NULL,
    access_token     VARCHAR(500) NOT NULL,
    refresh_token    VARCHAR(500) NOT NULL,
    public_key       VARCHAR(500),
    mp_user_id       VARCHAR(36)  NOT NULL,
    token_type       VARCHAR(50),
    scope            TEXT,
    live_mode        BOOLEAN,
    data_criacao     TIMESTAMP    NOT NULL,
    data_expiracao   TIMESTAMP    NOT NULL,
    terminal_id      VARCHAR(255),

    CONSTRAINT fk_mercado_pago_conta_empresa
        FOREIGN KEY (empresa_id)
            REFERENCES empresa (id)
);