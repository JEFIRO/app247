CREATE TABLE nota_fiscal_entrada (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    chave CHAR(44) NOT NULL,
    chave_concluida CHAR(44) NULL,
    modelo VARCHAR(2) NOT NULL,
    numero VARCHAR(9) NOT NULL,
    serie VARCHAR(3) NOT NULL,
    emitente_cnpj VARCHAR(14) NOT NULL,
    emitente_nome VARCHAR(180) NOT NULL,
    destinatario_documento VARCHAR(14) NULL,
    data_emissao DATETIME(6) NULL,
    valor_total DECIMAL(15,2) NOT NULL,
    origem VARCHAR(20) NOT NULL,
    hash_xml CHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    produtos_criados INT NOT NULL DEFAULT 0,
    entradas_lancadas INT NOT NULL DEFAULT 0,
    created_by CHAR(36) NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_nota_fiscal_entrada_id_tenant UNIQUE (id, empresa_id),
    -- Só a nota confirmada ocupa a chave: rascunhos repetidos não bloqueiam, a entrada no estoque sim.
    CONSTRAINT uk_nota_fiscal_entrada_concluida UNIQUE (empresa_id, chave_concluida),
    CONSTRAINT ck_nota_fiscal_entrada_status CHECK (status IN ('VALIDADA', 'CONCLUIDA')),
    CONSTRAINT fk_nota_fiscal_entrada_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT fk_nota_fiscal_entrada_usuario_tenant FOREIGN KEY (created_by, empresa_id)
        REFERENCES users (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_nota_fiscal_entrada_chave
    ON nota_fiscal_entrada (empresa_id, chave, created_at);

CREATE TABLE nota_fiscal_entrada_item (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    nota_id CHAR(36) NOT NULL,
    numero_item INT NOT NULL,
    codigo_fornecedor VARCHAR(60) NOT NULL,
    ean VARCHAR(14) NULL,
    descricao VARCHAR(180) NOT NULL,
    ncm VARCHAR(8) NULL,
    cest VARCHAR(7) NULL,
    cfop VARCHAR(4) NULL,
    unidade_comercial VARCHAR(6) NOT NULL,
    quantidade DECIMAL(15,4) NOT NULL,
    valor_unitario DECIMAL(21,10) NOT NULL,
    valor_total DECIMAL(15,2) NOT NULL,
    acao VARCHAR(20) NOT NULL,
    produto_id CHAR(36) NULL,
    nome_produto VARCHAR(180) NULL,
    preco_venda DECIMAL(15,6) NULL,
    categoria VARCHAR(50) NULL,
    unidade_medida VARCHAR(20) NOT NULL,
    fator_conversao DECIMAL(15,3) NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL,
    mensagem VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_nota_fiscal_item_numero UNIQUE (nota_id, numero_item),
    CONSTRAINT ck_nota_fiscal_item_acao CHECK (acao IN ('VINCULAR', 'CRIAR')),
    CONSTRAINT ck_nota_fiscal_item_fator CHECK (fator_conversao > 0),
    CONSTRAINT fk_nota_fiscal_item_nota_tenant FOREIGN KEY (nota_id, empresa_id)
        REFERENCES nota_fiscal_entrada (id, empresa_id),
    CONSTRAINT fk_nota_fiscal_item_produto_tenant FOREIGN KEY (produto_id, empresa_id)
        REFERENCES produto (id, empresa_id)
) ENGINE=InnoDB;
