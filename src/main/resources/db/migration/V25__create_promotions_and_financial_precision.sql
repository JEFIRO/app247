CREATE TABLE promocao
(
    id_promocao   VARCHAR(36)   NOT NULL,
    empresa_id    VARCHAR(36)   NOT NULL,
    condominio_id VARCHAR(36)   NULL,
    abrangencia   VARCHAR(30)   NOT NULL,
    nome          VARCHAR(150)  NOT NULL,
    descricao     VARCHAR(500)  NULL,
    tipo          VARCHAR(30)   NOT NULL,
    valor         DECIMAL(15,6) NOT NULL,
    inicio        DATETIME(6)   NOT NULL,
    fim           DATETIME(6)   NOT NULL,
    ativo         BOOLEAN       NOT NULL DEFAULT TRUE,
    prioridade    INT           NOT NULL DEFAULT 0,
    created_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_promocao),
    CONSTRAINT fk_promocao_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT fk_promocao_condominio FOREIGN KEY (condominio_id) REFERENCES condominio (id_condominio),
    CONSTRAINT ck_promocao_abrangencia CHECK (
        (abrangencia = 'EMPRESA' AND condominio_id IS NULL)
        OR (abrangencia = 'CONDOMINIO' AND condominio_id IS NOT NULL)
    ),
    CONSTRAINT ck_promocao_periodo CHECK (fim > inicio),
    CONSTRAINT ck_promocao_valor CHECK (valor >= 0)
);

CREATE INDEX idx_promocao_empresa_periodo
    ON promocao (empresa_id, ativo, inicio, fim);
CREATE INDEX idx_promocao_condominio_periodo
    ON promocao (condominio_id, ativo, inicio, fim);

CREATE TABLE promocao_produto
(
    id          VARCHAR(36) NOT NULL,
    promocao_id VARCHAR(36) NOT NULL,
    produto_id  VARCHAR(36) NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_promocao_produto UNIQUE (promocao_id, produto_id),
    CONSTRAINT fk_promocao_produto_promocao FOREIGN KEY (promocao_id)
        REFERENCES promocao (id_promocao),
    CONSTRAINT fk_promocao_produto_produto FOREIGN KEY (produto_id)
        REFERENCES produto (id_produto)
);

CREATE INDEX idx_promocao_produto_produto ON promocao_produto (produto_id);

ALTER TABLE produto MODIFY COLUMN preco DECIMAL(15,6) NOT NULL;
ALTER TABLE carrinho MODIFY COLUMN subtotal DECIMAL(15,6) NULL;
ALTER TABLE item MODIFY COLUMN unit_price DECIMAL(15,6) NOT NULL;
ALTER TABLE orders
    MODIFY COLUMN subtotal DECIMAL(15,6) NULL,
    MODIFY COLUMN desconto DECIMAL(15,6) NULL,
    MODIFY COLUMN total DECIMAL(15,6) NULL;
ALTER TABLE pagamento MODIFY COLUMN valor DECIMAL(15,6) NOT NULL;

ALTER TABLE item
    ADD COLUMN original_price DECIMAL(15,6) NULL,
    ADD COLUMN promocao_id VARCHAR(36) NULL,
    ADD COLUMN promotion_type VARCHAR(30) NULL,
    ADD COLUMN promotion_value DECIMAL(15,6) NULL,
    ADD COLUMN calculated_discount DECIMAL(15,6) NULL,
    ADD COLUMN calculated_subtotal DECIMAL(15,6) NULL;

UPDATE item
SET original_price = unit_price,
    calculated_discount = 0.000000,
    calculated_subtotal = unit_price * quantity;

ALTER TABLE item
    MODIFY COLUMN original_price DECIMAL(15,6) NOT NULL,
    MODIFY COLUMN calculated_discount DECIMAL(15,6) NOT NULL,
    MODIFY COLUMN calculated_subtotal DECIMAL(15,6) NOT NULL,
    ADD CONSTRAINT fk_item_promocao FOREIGN KEY (promocao_id) REFERENCES promocao (id_promocao);

CREATE INDEX idx_item_promocao ON item (promocao_id);

ALTER TABLE orders
    ADD COLUMN total_calculado DECIMAL(15,6) NULL,
    ADD COLUMN total_cobrado DECIMAL(15,6) NULL;

UPDATE orders
SET total_calculado = total,
    total_cobrado = total;
