CREATE TABLE endereco
(
    id_endereco varchar(36) PRIMARY KEY,
    rua         VARCHAR(255) NOT NULL,
    numero      VARCHAR(50)  NOT NULL,
    complemento VARCHAR(255),
    bairro      VARCHAR(255) NOT NULL,
    cidade      VARCHAR(255) NOT NULL,
    estado      VARCHAR(100) NOT NULL,
    cep         VARCHAR(20)  NOT NULL,

    empresa_id  varchar(36)  NOT NULL,
    CONSTRAINT fk_endereco_empresa
        FOREIGN KEY (empresa_id)
            REFERENCES empresa (id)
);