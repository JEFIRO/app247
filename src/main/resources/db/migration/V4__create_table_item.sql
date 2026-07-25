CREATE TABLE item
(
    id_item         varchar(36) PRIMARY KEY,
    id_produto      varchar(36)    NOT NULL,
    id_carrinho     varchar(36)    NOT NULL,
    barcode         VARCHAR(30)    NOT NULL,
    name            VARCHAR(100)   NOT NULL,
    unit_price      DECIMAL(10, 2) NOT NULL,
    quantity        INTEGER        NOT NULL,
    requires_weight BOOLEAN        NOT NULL DEFAULT TRUE,
    foto            varchar(255)   not null,
    expected_weight DECIMAL(10, 3),
    received_weight DECIMAL(10, 3),
    status          VARCHAR(30)    NOT NULL,
    created_at      TIMESTAMP               DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,


    empresa_id      varchar(36)    NOT NULL,
    CONSTRAINT fk_item_empresa
        FOREIGN KEY (empresa_id)
            REFERENCES empresa (id),

    CONSTRAINT fk_item_cart
        FOREIGN KEY (id_carrinho)
            REFERENCES carrinho (id_carrinho)
);