CREATE TABLE carrinho (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    condominio_id CHAR(36) NOT NULL,
    terminal_id CHAR(36) NOT NULL,
    status VARCHAR(30) NOT NULL,
    subtotal DECIMAL(15,6) NOT NULL DEFAULT 0.000000,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_carrinho_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT uk_carrinho_contexto UNIQUE (id, empresa_id, condominio_id, terminal_id),
    CONSTRAINT fk_carrinho_condominio_tenant FOREIGN KEY (condominio_id, empresa_id)
        REFERENCES condominio (id, empresa_id),
    CONSTRAINT fk_carrinho_terminal_condominio FOREIGN KEY (terminal_id, condominio_id)
        REFERENCES terminal (id, condominio_id)
) ENGINE=InnoDB;

CREATE INDEX idx_carrinho_terminal_status ON carrinho (terminal_id, status, created_at);
CREATE INDEX idx_carrinho_empresa_created ON carrinho (empresa_id, created_at);

CREATE TABLE cart_item (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    carrinho_id CHAR(36) NOT NULL,
    produto_id CHAR(36) NOT NULL,
    codigo_barras_id CHAR(36) NULL,
    codigo_barras VARCHAR(80) NULL,
    nome VARCHAR(180) NOT NULL,
    foto VARCHAR(500) NULL,
    unidade_medida VARCHAR(20) NOT NULL,
    quantidade DECIMAL(15,3) NOT NULL,
    preco_original DECIMAL(15,6) NOT NULL,
    preco_unitario_aplicado DECIMAL(15,6) NOT NULL,
    promocao_id CHAR(36) NULL,
    tipo_promocao VARCHAR(30) NULL,
    valor_promocao DECIMAL(15,6) NULL,
    desconto_calculado DECIMAL(15,6) NOT NULL,
    subtotal_calculado DECIMAL(15,6) NOT NULL,
    requer_peso BOOLEAN NOT NULL DEFAULT FALSE,
    peso_esperado DECIMAL(15,3) NULL,
    peso_recebido DECIMAL(15,3) NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cart_item_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT uk_cart_item_produto UNIQUE (carrinho_id, produto_id),
    CONSTRAINT ck_cart_item_quantidade CHECK (quantidade > 0),
    CONSTRAINT fk_cart_item_carrinho_tenant FOREIGN KEY (carrinho_id, empresa_id)
        REFERENCES carrinho (id, empresa_id),
    CONSTRAINT fk_cart_item_produto_tenant FOREIGN KEY (produto_id, empresa_id)
        REFERENCES produto (id, empresa_id),
    CONSTRAINT fk_cart_item_barcode_produto FOREIGN KEY (codigo_barras_id, produto_id, empresa_id)
        REFERENCES produto_codigo_barras (id, produto_id, empresa_id),
    CONSTRAINT fk_cart_item_promocao_tenant FOREIGN KEY (promocao_id, empresa_id)
        REFERENCES promocao (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_cart_item_carrinho ON cart_item (carrinho_id);

CREATE TABLE orders (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    condominio_id CHAR(36) NOT NULL,
    terminal_id CHAR(36) NOT NULL,
    carrinho_id CHAR(36) NOT NULL,
    user_id CHAR(36) NULL,
    status VARCHAR(30) NOT NULL,
    origin_request VARCHAR(30) NOT NULL,
    subtotal DECIMAL(15,6) NOT NULL,
    desconto DECIMAL(15,6) NOT NULL,
    total_calculado DECIMAL(15,6) NOT NULL,
    total_cobrado DECIMAL(15,6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    paid_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_orders_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT uk_orders_carrinho UNIQUE (carrinho_id),
    CONSTRAINT fk_orders_carrinho_contexto FOREIGN KEY (carrinho_id, empresa_id, condominio_id, terminal_id)
        REFERENCES carrinho (id, empresa_id, condominio_id, terminal_id),
    CONSTRAINT fk_orders_user_tenant FOREIGN KEY (user_id, empresa_id)
        REFERENCES users (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_orders_empresa_created ON orders (empresa_id, created_at);
CREATE INDEX idx_orders_terminal_status_created ON orders (terminal_id, status, created_at);
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at);
CREATE INDEX idx_orders_status_updated ON orders (status, updated_at);

CREATE TABLE order_item (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    order_id CHAR(36) NOT NULL,
    produto_id CHAR(36) NOT NULL,
    promocao_id CHAR(36) NULL,
    codigo_interno VARCHAR(80) NOT NULL,
    codigo_barras VARCHAR(80) NULL,
    nome VARCHAR(180) NOT NULL,
    unidade_medida VARCHAR(20) NOT NULL,
    quantidade DECIMAL(15,3) NOT NULL,
    preco_original DECIMAL(15,6) NOT NULL,
    preco_unitario_aplicado DECIMAL(15,6) NOT NULL,
    tipo_promocao VARCHAR(30) NULL,
    valor_promocao DECIMAL(15,6) NULL,
    desconto_calculado DECIMAL(15,6) NOT NULL,
    subtotal_calculado DECIMAL(15,6) NOT NULL,
    fiscal_snapshot_version VARCHAR(30) NULL,
    fiscal_snapshot JSON NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_order_item_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT uk_order_item_produto UNIQUE (order_id, produto_id),
    CONSTRAINT ck_order_item_quantidade CHECK (quantidade > 0),
    CONSTRAINT fk_order_item_order_tenant FOREIGN KEY (order_id, empresa_id)
        REFERENCES orders (id, empresa_id),
    CONSTRAINT fk_order_item_produto_tenant FOREIGN KEY (produto_id, empresa_id)
        REFERENCES produto (id, empresa_id),
    CONSTRAINT fk_order_item_promocao_tenant FOREIGN KEY (promocao_id, empresa_id)
        REFERENCES promocao (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_order_item_order ON order_item (order_id);
CREATE INDEX idx_order_item_produto ON order_item (produto_id, created_at);

CREATE TABLE movimentacao_estoque (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    estoque_condominio_id CHAR(36) NOT NULL,
    order_id CHAR(36) NULL,
    order_item_id CHAR(36) NULL,
    tipo VARCHAR(40) NOT NULL,
    quantidade DECIMAL(15,3) NOT NULL,
    saldo_anterior DECIMAL(15,3) NOT NULL,
    saldo_posterior DECIMAL(15,3) NOT NULL,
    motivo VARCHAR(300) NULL,
    chave_idempotencia VARCHAR(160) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_movimento_idempotencia UNIQUE (empresa_id, chave_idempotencia),
    CONSTRAINT fk_movimento_estoque_tenant FOREIGN KEY (estoque_condominio_id, empresa_id)
        REFERENCES estoque_condominio (id, empresa_id),
    CONSTRAINT fk_movimento_order_tenant FOREIGN KEY (order_id, empresa_id)
        REFERENCES orders (id, empresa_id),
    CONSTRAINT fk_movimento_item_tenant FOREIGN KEY (order_item_id, empresa_id)
        REFERENCES order_item (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_movimento_estoque_created ON movimentacao_estoque (estoque_condominio_id, created_at);
CREATE INDEX idx_movimento_order ON movimentacao_estoque (order_id);
