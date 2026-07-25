CREATE TABLE orders
(
    id_order    varchar(36) PRIMARY KEY,
    id_carrinho varchar(36) NOT NULL,
    id_user     varchar(36),
    id_terminal varchar(36),

    subtotal    DECIMAL(10, 2),
    desconto    DECIMAL(10, 2),
    total       DECIMAL(10, 2),

    status      VARCHAR(20) DEFAULT 'PENDING',

    created_at  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NULL,
    paid_at     TIMESTAMP   NULL,

    empresa_id  varchar(36) NOT NULL,
    CONSTRAINT fk_order_empresa
        FOREIGN KEY (empresa_id)
            REFERENCES empresa (id),

    CONSTRAINT fk_order_carrinho FOREIGN KEY (id_carrinho) REFERENCES carrinho (id_carrinho),
    CONSTRAINT fk_order_user FOREIGN KEY (id_user) REFERENCES users (id_user)
);