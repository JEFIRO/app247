CREATE TABLE orders
(
    order_id    VARCHAR(36) PRIMARY KEY,
    carrinho_id VARCHAR(36) NOT NULL,
    user_id     VARCHAR(36),
    terminal_id varchar(36),

    subtotal    DECIMAL(10, 2),
    desconto    DECIMAL(10, 2),
    total       DECIMAL(10, 2),

    status      VARCHAR(20) DEFAULT 'PENDING',

    created_at  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NULL,
    paid_at     TIMESTAMP   NULL,

    CONSTRAINT fk_order_carrinho FOREIGN KEY (carrinho_id) REFERENCES carrinho (carrinho_id),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users (uuid_user)
);