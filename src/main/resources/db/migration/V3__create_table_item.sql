CREATE TABLE item
(
    item_id         CHAR(36) PRIMARY KEY,
    produto_id      VARCHAR(40)    NOT NULL,
    carrinho_id     CHAR(36)       NOT NULL,
    barcode         VARCHAR(30)    NOT NULL,
    name            VARCHAR(100)   NOT NULL,
    unit_price      DECIMAL(10, 2) NOT NULL,
    quantity        INTEGER        NOT NULL,
    requires_weight BOOLEAN        NOT NULL DEFAULT TRUE,
    expected_weight DECIMAL(10, 3),
    received_weight DECIMAL(10, 3),
    status          VARCHAR(30)    NOT NULL,
    created_at      TIMESTAMP               DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_item_cart
        FOREIGN KEY (carrinho_id)
            REFERENCES carrinho (carrinho_id)
);