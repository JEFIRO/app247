CREATE TABLE perfil_tributario (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    nome VARCHAR(120) NOT NULL,
    descricao VARCHAR(500) NULL,
    versao VARCHAR(30) NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_perfil_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT uk_perfil_empresa_nome UNIQUE (empresa_id, nome),
    CONSTRAINT fk_perfil_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id)
) ENGINE=InnoDB;

CREATE TABLE produto (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    codigo_interno VARCHAR(80) NOT NULL,
    nome VARCHAR(180) NOT NULL,
    descricao VARCHAR(1000) NULL,
    preco_venda DECIMAL(15,6) NOT NULL,
    peso DECIMAL(15,3) NULL,
    peso_tolerancia DECIMAL(15,3) NULL,
    categoria VARCHAR(50) NOT NULL,
    unidade_medida VARCHAR(20) NOT NULL,
    foto VARCHAR(500) NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_produto_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT uk_produto_empresa_sku UNIQUE (empresa_id, codigo_interno),
    CONSTRAINT ck_produto_preco CHECK (preco_venda >= 0),
    CONSTRAINT ck_produto_peso CHECK (peso IS NULL OR peso >= 0),
    CONSTRAINT ck_produto_tolerancia CHECK (peso_tolerancia IS NULL OR peso_tolerancia >= 0),
    CONSTRAINT fk_produto_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id)
) ENGINE=InnoDB;

CREATE INDEX idx_produto_empresa_ativo ON produto (empresa_id, ativo, nome);
CREATE INDEX idx_produto_empresa_updated ON produto (empresa_id, updated_at);

CREATE TABLE produto_codigo_barras (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    produto_id CHAR(36) NOT NULL,
    codigo_barras VARCHAR(80) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    principal BOOLEAN NOT NULL DEFAULT FALSE,
    principal_produto_id CHAR(36) GENERATED ALWAYS AS (
        CASE WHEN principal THEN produto_id ELSE NULL END
    ) STORED,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_barcode_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT uk_barcode_id_produto_empresa UNIQUE (id, produto_id, empresa_id),
    CONSTRAINT uk_barcode_empresa_codigo UNIQUE (empresa_id, codigo_barras),
    CONSTRAINT uk_barcode_principal_produto UNIQUE (principal_produto_id),
    CONSTRAINT fk_barcode_produto_tenant FOREIGN KEY (produto_id, empresa_id)
        REFERENCES produto (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_barcode_produto_ativo ON produto_codigo_barras (produto_id, ativo, principal);

CREATE TABLE produto_fiscal (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    produto_id CHAR(36) NOT NULL,
    perfil_tributario_id CHAR(36) NULL,
    ncm CHAR(8) NULL,
    cest VARCHAR(7) NULL,
    origem_mercadoria VARCHAR(2) NULL,
    unidade_tributavel VARCHAR(6) NULL,
    fator_conversao_tributavel DECIMAL(15,6) NULL,
    gtin_tributavel VARCHAR(14) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_produto_fiscal_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT uk_produto_fiscal_produto UNIQUE (produto_id),
    CONSTRAINT ck_produto_fiscal_fator CHECK (
        fator_conversao_tributavel IS NULL OR fator_conversao_tributavel > 0
    ),
    CONSTRAINT fk_produto_fiscal_produto_tenant FOREIGN KEY (produto_id, empresa_id)
        REFERENCES produto (id, empresa_id),
    CONSTRAINT fk_produto_fiscal_perfil_tenant FOREIGN KEY (perfil_tributario_id, empresa_id)
        REFERENCES perfil_tributario (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_produto_fiscal_ncm ON produto_fiscal (empresa_id, ncm);
