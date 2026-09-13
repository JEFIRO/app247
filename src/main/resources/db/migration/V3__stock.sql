CREATE TABLE estoque_condominio (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    condominio_id CHAR(36) NOT NULL,
    produto_id CHAR(36) NOT NULL,
    quantidade DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_estoque_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT uk_estoque_condominio_produto UNIQUE (condominio_id, produto_id),
    CONSTRAINT fk_estoque_condominio_tenant FOREIGN KEY (condominio_id, empresa_id)
        REFERENCES condominio (id, empresa_id),
    CONSTRAINT fk_estoque_produto_tenant FOREIGN KEY (produto_id, empresa_id)
        REFERENCES produto (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_estoque_condominio_ativo ON estoque_condominio (condominio_id, ativo, produto_id);
CREATE INDEX idx_estoque_produto_ativo ON estoque_condominio (produto_id, ativo, condominio_id);
CREATE INDEX idx_estoque_empresa_updated ON estoque_condominio (empresa_id, updated_at);
