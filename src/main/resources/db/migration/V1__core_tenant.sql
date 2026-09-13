CREATE TABLE empresa (
    id CHAR(36) NOT NULL,
    tenant_id CHAR(36) NOT NULL,
    razao_social VARCHAR(150) NOT NULL,
    nome_fantasia VARCHAR(150) NOT NULL,
    cnpj VARCHAR(18) NOT NULL,
    email VARCHAR(180) NOT NULL,
    telefone VARCHAR(20) NULL,
    cep VARCHAR(9) NULL,
    logradouro VARCHAR(180) NULL,
    numero VARCHAR(20) NULL,
    bairro VARCHAR(100) NULL,
    cidade VARCHAR(100) NULL,
    estado CHAR(2) NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_empresa_tenant UNIQUE (tenant_id),
    CONSTRAINT uk_empresa_cnpj UNIQUE (cnpj),
    CONSTRAINT uk_empresa_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE endereco (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    rua VARCHAR(180) NOT NULL,
    numero VARCHAR(20) NOT NULL,
    complemento VARCHAR(100) NULL,
    bairro VARCHAR(100) NOT NULL,
    cidade VARCHAR(100) NOT NULL,
    estado CHAR(2) NOT NULL,
    cep VARCHAR(9) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_endereco_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT fk_endereco_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id)
) ENGINE=InnoDB;

CREATE TABLE condominio (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    endereco_id CHAR(36) NULL,
    nome VARCHAR(150) NOT NULL,
    cnpj VARCHAR(18) NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_condominio_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT uk_condominio_endereco UNIQUE (endereco_id),
    CONSTRAINT uk_condominio_empresa_cnpj UNIQUE (empresa_id, cnpj),
    CONSTRAINT fk_condominio_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT fk_condominio_endereco_tenant FOREIGN KEY (endereco_id, empresa_id)
        REFERENCES endereco (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_condominio_empresa_ativo ON condominio (empresa_id, ativo, nome);

CREATE TABLE users (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    condominio_id CHAR(36) NULL,
    nome VARCHAR(100) NOT NULL,
    sobrenome VARCHAR(100) NULL,
    email VARCHAR(180) NOT NULL,
    senha VARCHAR(255) NOT NULL,
    cpf VARCHAR(14) NOT NULL,
    telefone VARCHAR(20) NULL,
    data_nascimento DATE NULL,
    foto_perfil VARCHAR(500) NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    email_verificado BOOLEAN NOT NULL DEFAULT FALSE,
    role VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    ultimo_login DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT uk_users_cpf UNIQUE (cpf),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT fk_users_condominio_tenant FOREIGN KEY (condominio_id, empresa_id)
        REFERENCES condominio (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_users_empresa_ativo ON users (empresa_id, ativo, nome);
CREATE INDEX idx_users_condominio ON users (condominio_id);

CREATE TABLE terminal (
    id CHAR(36) NOT NULL,
    condominio_id CHAR(36) NOT NULL,
    nome VARCHAR(120) NOT NULL,
    codigo VARCHAR(80) NOT NULL,
    serial_number VARCHAR(120) NULL,
    mac_address VARCHAR(30) NULL,
    ip_address VARCHAR(45) NULL,
    versao_software VARCHAR(60) NULL,
    mercado_pago_terminal_id VARCHAR(120) NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(30) NOT NULL,
    last_ping DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_terminal_id_condominio UNIQUE (id, condominio_id),
    CONSTRAINT uk_terminal_condominio_codigo UNIQUE (condominio_id, codigo),
    CONSTRAINT uk_terminal_serial UNIQUE (serial_number),
    CONSTRAINT uk_terminal_mp_point UNIQUE (mercado_pago_terminal_id),
    CONSTRAINT fk_terminal_condominio FOREIGN KEY (condominio_id) REFERENCES condominio (id)
) ENGINE=InnoDB;

CREATE INDEX idx_terminal_condominio_ativo ON terminal (condominio_id, ativo, nome);
CREATE INDEX idx_terminal_last_ping ON terminal (last_ping);
