CREATE TABLE carrinho
(
    id_carrinho varchar(36) PRIMARY KEY,
    id_terminal varchar(36) NOT NULL,
    status      VARCHAR(30) NOT NULL,
    subtotal    DECIMAL(10, 2),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    empresa_id  varchar(36) NOT NULL,
    CONSTRAINT fk_carrinho_empresa
        FOREIGN KEY (empresa_id)
            REFERENCES empresa (id)
);