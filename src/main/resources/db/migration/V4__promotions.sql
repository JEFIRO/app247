CREATE TABLE promocao (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    condominio_id CHAR(36) NULL,
    abrangencia VARCHAR(30) NOT NULL,
    nome VARCHAR(150) NOT NULL,
    descricao VARCHAR(500) NULL,
    tipo VARCHAR(30) NOT NULL,
    valor DECIMAL(15,6) NOT NULL,
    inicio DATETIME(6) NOT NULL,
    fim DATETIME(6) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    prioridade INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_promocao_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT ck_promocao_periodo CHECK (fim > inicio),
    CONSTRAINT ck_promocao_valor CHECK (valor >= 0),
    CONSTRAINT ck_promocao_abrangencia CHECK (
        (abrangencia = 'EMPRESA' AND condominio_id IS NULL)
        OR (abrangencia = 'CONDOMINIO' AND condominio_id IS NOT NULL)
    ),
    CONSTRAINT fk_promocao_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT fk_promocao_condominio_tenant FOREIGN KEY (condominio_id, empresa_id)
        REFERENCES condominio (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_promocao_empresa_periodo ON promocao (empresa_id, ativo, inicio, fim);
CREATE INDEX idx_promocao_condominio_periodo ON promocao (condominio_id, ativo, inicio, fim);

CREATE TABLE promocao_produto (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    promocao_id CHAR(36) NOT NULL,
    produto_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_promocao_produto UNIQUE (promocao_id, produto_id),
    CONSTRAINT fk_promocao_produto_promocao_tenant FOREIGN KEY (promocao_id, empresa_id)
        REFERENCES promocao (id, empresa_id),
    CONSTRAINT fk_promocao_produto_produto_tenant FOREIGN KEY (produto_id, empresa_id)
        REFERENCES produto (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_promocao_produto_produto ON promocao_produto (produto_id, promocao_id);
