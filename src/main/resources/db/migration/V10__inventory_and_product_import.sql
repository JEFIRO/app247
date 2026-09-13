CREATE TABLE inventario (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    local_tipo VARCHAR(30) NOT NULL,
    local_id CHAR(36) NOT NULL,
    status VARCHAR(30) NOT NULL,
    descricao VARCHAR(180) NULL,
    observacao VARCHAR(600) NULL,
    contagem_cega BOOLEAN NOT NULL DEFAULT FALSE,
    created_by CHAR(36) NULL,
    finalized_by CHAR(36) NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    finalized_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventario_id_tenant UNIQUE (id, empresa_id),
    CONSTRAINT ck_inventario_local_tipo CHECK (local_tipo IN ('ESTOQUE_EMPRESA', 'CONDOMINIO')),
    CONSTRAINT fk_inventario_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT fk_inventario_created_by_tenant FOREIGN KEY (created_by, empresa_id)
        REFERENCES users (id, empresa_id),
    CONSTRAINT fk_inventario_finalized_by_tenant FOREIGN KEY (finalized_by, empresa_id)
        REFERENCES users (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_inventario_empresa_status_data
    ON inventario (empresa_id, status, created_at);
CREATE INDEX idx_inventario_local
    ON inventario (empresa_id, local_tipo, local_id, created_at);

CREATE TABLE inventario_item (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    inventario_id CHAR(36) NOT NULL,
    produto_id CHAR(36) NOT NULL,
    estoque_empresa_id CHAR(36) NULL,
    estoque_condominio_id CHAR(36) NULL,
    saldo_sistema_snapshot DECIMAL(15,3) NOT NULL,
    estoque_version_snapshot BIGINT NOT NULL,
    estoque_updated_at_snapshot DATETIME(6) NOT NULL,
    quantidade_contada DECIMAL(15,3) NULL,
    diferenca DECIMAL(15,3) NULL,
    saldo_atual_conflito DECIMAL(15,3) NULL,
    motivo_ajuste VARCHAR(300) NULL,
    contado_por CHAR(36) NULL,
    contado_em DATETIME(6) NULL,
    status VARCHAR(30) NOT NULL,
    ajuste_aplicado BOOLEAN NOT NULL DEFAULT FALSE,
    ajuste_aplicado_em DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventario_item_id_tenant UNIQUE (id, empresa_id),
    CONSTRAINT uk_inventario_item_produto UNIQUE (inventario_id, produto_id),
    CONSTRAINT ck_inventario_item_local_unico CHECK (
        (estoque_empresa_id IS NOT NULL AND estoque_condominio_id IS NULL)
        OR
        (estoque_empresa_id IS NULL AND estoque_condominio_id IS NOT NULL)
    ),
    CONSTRAINT fk_inventario_item_inventario_tenant FOREIGN KEY (inventario_id, empresa_id)
        REFERENCES inventario (id, empresa_id),
    CONSTRAINT fk_inventario_item_produto_tenant FOREIGN KEY (produto_id, empresa_id)
        REFERENCES produto (id, empresa_id),
    CONSTRAINT fk_inventario_item_estoque_empresa_tenant FOREIGN KEY (estoque_empresa_id, empresa_id)
        REFERENCES estoque_empresa (id, empresa_id),
    CONSTRAINT fk_inventario_item_estoque_condominio_tenant FOREIGN KEY (estoque_condominio_id, empresa_id)
        REFERENCES estoque_condominio (id, empresa_id),
    CONSTRAINT fk_inventario_item_contado_por_tenant FOREIGN KEY (contado_por, empresa_id)
        REFERENCES users (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_inventario_item_status
    ON inventario_item (inventario_id, status, ajuste_aplicado);
CREATE INDEX idx_inventario_item_produto
    ON inventario_item (empresa_id, produto_id, created_at);

CREATE TABLE importacao_produto (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    nome_arquivo VARCHAR(255) NOT NULL,
    hash_arquivo CHAR(64) NOT NULL,
    status VARCHAR(40) NOT NULL,
    modo VARCHAR(40) NOT NULL,
    total_linhas INT NOT NULL DEFAULT 0,
    total_validas INT NOT NULL DEFAULT 0,
    total_avisos INT NOT NULL DEFAULT 0,
    total_erros INT NOT NULL DEFAULT 0,
    produtos_importados INT NOT NULL DEFAULT 0,
    barcodes_importados INT NOT NULL DEFAULT 0,
    estoques_criados INT NOT NULL DEFAULT 0,
    created_by CHAR(36) NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    validated_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_importacao_produto_id_tenant UNIQUE (id, empresa_id),
    CONSTRAINT fk_importacao_produto_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT fk_importacao_produto_usuario_tenant FOREIGN KEY (created_by, empresa_id)
        REFERENCES users (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_importacao_produto_empresa_status_data
    ON importacao_produto (empresa_id, status, created_at);
CREATE INDEX idx_importacao_produto_hash
    ON importacao_produto (empresa_id, hash_arquivo, created_at);

CREATE TABLE importacao_produto_item (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    importacao_id CHAR(36) NOT NULL,
    aba VARCHAR(40) NOT NULL,
    linha INT NOT NULL,
    codigo_interno VARCHAR(80) NULL,
    status VARCHAR(30) NOT NULL,
    mensagem VARCHAR(1000) NULL,
    dados_normalizados LONGTEXT NULL,
    produto_id CHAR(36) NULL,
    created_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_importacao_item_id_tenant UNIQUE (id, empresa_id),
    CONSTRAINT uk_importacao_item_linha UNIQUE (importacao_id, aba, linha),
    CONSTRAINT fk_importacao_item_importacao_tenant FOREIGN KEY (importacao_id, empresa_id)
        REFERENCES importacao_produto (id, empresa_id),
    CONSTRAINT fk_importacao_item_produto_tenant FOREIGN KEY (produto_id, empresa_id)
        REFERENCES produto (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_importacao_item_status
    ON importacao_produto_item (importacao_id, status, linha);
CREATE INDEX idx_importacao_item_sku
    ON importacao_produto_item (empresa_id, codigo_interno, created_at);

ALTER TABLE movimentacao_estoque
    ADD COLUMN inventario_id CHAR(36) NULL AFTER transferencia_id,
    ADD COLUMN inventario_item_id CHAR(36) NULL AFTER inventario_id,
    ADD CONSTRAINT fk_movimento_inventario_tenant
        FOREIGN KEY (inventario_id, empresa_id) REFERENCES inventario (id, empresa_id),
    ADD CONSTRAINT fk_movimento_inventario_item_tenant
        FOREIGN KEY (inventario_item_id, empresa_id) REFERENCES inventario_item (id, empresa_id);

CREATE INDEX idx_movimento_inventario
    ON movimentacao_estoque (inventario_id, created_at);
