CREATE TABLE estoque_empresa (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    produto_id CHAR(36) NOT NULL,
    quantidade DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_estoque_empresa_id_tenant UNIQUE (id, empresa_id),
    CONSTRAINT uk_estoque_empresa_produto UNIQUE (empresa_id, produto_id),
    CONSTRAINT fk_estoque_empresa_tenant FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT fk_estoque_empresa_produto_tenant FOREIGN KEY (produto_id, empresa_id)
        REFERENCES produto (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_estoque_empresa_listagem
    ON estoque_empresa (empresa_id, ativo, produto_id);

CREATE TABLE planograma (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    nome VARCHAR(150) NOT NULL,
    descricao VARCHAR(600) NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_planograma_id_tenant UNIQUE (id, empresa_id),
    CONSTRAINT uk_planograma_empresa_nome UNIQUE (empresa_id, nome),
    CONSTRAINT fk_planograma_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id)
) ENGINE=InnoDB;

CREATE INDEX idx_planograma_empresa_ativo ON planograma (empresa_id, ativo, nome);

CREATE TABLE planograma_posicao (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    planograma_id CHAR(36) NOT NULL,
    setor VARCHAR(100) NULL,
    corredor VARCHAR(50) NULL,
    estante VARCHAR(50) NULL,
    modulo VARCHAR(50) NULL,
    prateleira VARCHAR(50) NULL,
    posicao VARCHAR(50) NULL,
    ordem_exibicao INT NOT NULL DEFAULT 0,
    x DECIMAL(12,3) NULL,
    y DECIMAL(12,3) NULL,
    largura DECIMAL(12,3) NULL,
    altura DECIMAL(12,3) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_planograma_posicao_id_tenant UNIQUE (id, empresa_id),
    CONSTRAINT fk_planograma_posicao_tenant FOREIGN KEY (planograma_id, empresa_id)
        REFERENCES planograma (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_planograma_posicao_ordem
    ON planograma_posicao (planograma_id, ordem_exibicao, id);

CREATE TABLE planograma_produto (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    planograma_posicao_id CHAR(36) NOT NULL,
    produto_id CHAR(36) NOT NULL,
    facings INT NOT NULL DEFAULT 1,
    capacidade DECIMAL(15,3) NULL,
    quantidade_ideal DECIMAL(15,3) NULL,
    quantidade_minima DECIMAL(15,3) NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_planograma_produto_id_tenant UNIQUE (id, empresa_id),
    CONSTRAINT uk_planograma_posicao_produto UNIQUE (planograma_posicao_id, produto_id),
    CONSTRAINT ck_planograma_produto_facings CHECK (facings > 0),
    CONSTRAINT ck_planograma_produto_capacidade CHECK (capacidade IS NULL OR capacidade >= 0),
    CONSTRAINT ck_planograma_produto_ideal CHECK (quantidade_ideal IS NULL OR quantidade_ideal >= 0),
    CONSTRAINT ck_planograma_produto_minima CHECK (quantidade_minima IS NULL OR quantidade_minima >= 0),
    CONSTRAINT fk_planograma_produto_posicao_tenant FOREIGN KEY (planograma_posicao_id, empresa_id)
        REFERENCES planograma_posicao (id, empresa_id),
    CONSTRAINT fk_planograma_produto_catalogo_tenant FOREIGN KEY (produto_id, empresa_id)
        REFERENCES produto (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_planograma_produto_localizacao
    ON planograma_produto (empresa_id, produto_id, ativo);

CREATE TABLE transferencia_estoque (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    origem_tipo VARCHAR(30) NOT NULL,
    origem_id CHAR(36) NOT NULL,
    destino_tipo VARCHAR(30) NOT NULL,
    destino_id CHAR(36) NOT NULL,
    status VARCHAR(30) NOT NULL,
    observacao VARCHAR(600) NULL,
    created_by CHAR(36) NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_transferencia_id_tenant UNIQUE (id, empresa_id),
    CONSTRAINT fk_transferencia_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT fk_transferencia_usuario_tenant FOREIGN KEY (created_by, empresa_id)
        REFERENCES users (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_transferencia_empresa_status_data
    ON transferencia_estoque (empresa_id, status, created_at);
CREATE INDEX idx_transferencia_destino
    ON transferencia_estoque (empresa_id, destino_tipo, destino_id, created_at);

CREATE TABLE transferencia_estoque_item (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    transferencia_id CHAR(36) NOT NULL,
    produto_id CHAR(36) NOT NULL,
    quantidade DECIMAL(15,3) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_transferencia_item_id_tenant UNIQUE (id, empresa_id),
    CONSTRAINT uk_transferencia_item_produto UNIQUE (transferencia_id, produto_id),
    CONSTRAINT ck_transferencia_item_quantidade CHECK (quantidade > 0),
    CONSTRAINT fk_transferencia_item_transferencia_tenant FOREIGN KEY (transferencia_id, empresa_id)
        REFERENCES transferencia_estoque (id, empresa_id),
    CONSTRAINT fk_transferencia_item_produto_tenant FOREIGN KEY (produto_id, empresa_id)
        REFERENCES produto (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_transferencia_item_produto
    ON transferencia_estoque_item (empresa_id, produto_id, created_at);

ALTER TABLE movimentacao_estoque
    MODIFY COLUMN estoque_condominio_id CHAR(36) NULL,
    ADD COLUMN estoque_empresa_id CHAR(36) NULL AFTER estoque_condominio_id,
    ADD COLUMN transferencia_id CHAR(36) NULL AFTER order_item_id,
    ADD COLUMN created_by CHAR(36) NULL AFTER transferencia_id,
    ADD CONSTRAINT fk_movimento_estoque_empresa_tenant
        FOREIGN KEY (estoque_empresa_id, empresa_id) REFERENCES estoque_empresa (id, empresa_id),
    ADD CONSTRAINT fk_movimento_transferencia_tenant
        FOREIGN KEY (transferencia_id, empresa_id) REFERENCES transferencia_estoque (id, empresa_id),
    ADD CONSTRAINT fk_movimento_usuario_tenant
        FOREIGN KEY (created_by, empresa_id) REFERENCES users (id, empresa_id),
    ADD CONSTRAINT ck_movimento_local_unico CHECK (
        (estoque_condominio_id IS NOT NULL AND estoque_empresa_id IS NULL)
        OR
        (estoque_condominio_id IS NULL AND estoque_empresa_id IS NOT NULL)
    );

CREATE INDEX idx_movimento_estoque_empresa_created
    ON movimentacao_estoque (estoque_empresa_id, created_at);
CREATE INDEX idx_movimento_transferencia
    ON movimentacao_estoque (transferencia_id, created_at);
