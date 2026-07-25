CREATE TABLE empresa
(
    id            VARCHAR(36) primary key,
    razao_social  VARCHAR(255) NOT NULL,
    nome_fantasia VARCHAR(255) NOT NULL,
    cnpj          VARCHAR(18)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    telefone      VARCHAR(20),
    cep           VARCHAR(10),
    logradouro    VARCHAR(255),
    numero        VARCHAR(20),
    bairro        VARCHAR(100),
    cidade        VARCHAR(100),
    estado        VARCHAR(2),
    tenant_id     VARCHAR(36)  NOT NULL UNIQUE,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    data_cadastro DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP

);