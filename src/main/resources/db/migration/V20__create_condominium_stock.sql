CREATE TABLE estoque_condominio
(
    id             VARCHAR(36) PRIMARY KEY,
    condominio_id  VARCHAR(36)    NOT NULL,
    produto_id     VARCHAR(36)    NOT NULL,
    quantidade     DECIMAL(15, 3) NOT NULL DEFAULT 0,
    ativo          BOOLEAN        NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_estoque_condominio_produto UNIQUE (condominio_id, produto_id),
    CONSTRAINT fk_estoque_condominio FOREIGN KEY (condominio_id) REFERENCES condominio (id_condominio),
    CONSTRAINT fk_estoque_produto FOREIGN KEY (produto_id) REFERENCES produto (id_produto)
);

-- O saldo legado só tem destino inequívoco em empresas com exatamente um condomínio.
INSERT INTO estoque_condominio (id, condominio_id, produto_id, quantidade, ativo)
SELECT UUID(), MIN(c.id_condominio), p.id_produto, p.quantidade, TRUE
FROM produto p
JOIN condominio c ON c.empresa_id = p.empresa_id
GROUP BY p.id_produto, p.quantidade
HAVING COUNT(c.id_condominio) = 1;

CREATE TABLE movimentacao_estoque
(
    id                    VARCHAR(36) PRIMARY KEY,
    estoque_id            VARCHAR(36)    NOT NULL,
    tipo                  VARCHAR(30)     NOT NULL,
    quantidade            DECIMAL(15, 3) NOT NULL,
    quantidade_anterior   DECIMAL(15, 3) NOT NULL,
    quantidade_posterior  DECIMAL(15, 3) NOT NULL,
    order_id              VARCHAR(36),
    item_id               VARCHAR(36),
    motivo                VARCHAR(255),
    chave_idempotencia    VARCHAR(160)    NOT NULL,
    created_at            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_movimento_chave UNIQUE (chave_idempotencia),
    CONSTRAINT fk_movimento_estoque FOREIGN KEY (estoque_id) REFERENCES estoque_condominio (id),
    CONSTRAINT fk_movimento_order FOREIGN KEY (order_id) REFERENCES orders (id_order),
    CONSTRAINT fk_movimento_item FOREIGN KEY (item_id) REFERENCES item (id_item)
);

ALTER TABLE item
    ADD CONSTRAINT fk_item_produto FOREIGN KEY (id_produto) REFERENCES produto (id_produto);

ALTER TABLE carrinho
    ADD CONSTRAINT fk_carrinho_terminal FOREIGN KEY (id_terminal) REFERENCES terminal (id_terminal);

ALTER TABLE orders
    ADD CONSTRAINT uk_order_carrinho UNIQUE (id_carrinho);

ALTER TABLE produto
    DROP INDEX codigo,
    DROP COLUMN quantidade,
    ADD CONSTRAINT uk_produto_empresa_codigo UNIQUE (empresa_id, codigo);
