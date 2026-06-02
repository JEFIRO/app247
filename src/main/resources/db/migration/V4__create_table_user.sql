CREATE TABLE users
(

    user_id          INTEGER PRIMARY KEY AUTO_INCREMENT,
    condominio_id    INTEGER(36),
    uuid_user        VARCHAR(36)  NOT NULL UNIQUE,

    nome             VARCHAR(100) NOT NULL,
    sobrenome        VARCHAR(100) NOT NULL,

    email            VARCHAR(150) NOT NULL UNIQUE,
    senha            VARCHAR(255) NOT NULL,

    cpf              VARCHAR(11)  NOT NULL UNIQUE,
    telefone         VARCHAR(20),

    data_nascimento  DATE,

    foto_perfil      TEXT,

    ativo            BOOLEAN   DEFAULT TRUE,
    email_verificado BOOLEAN   DEFAULT FALSE,

    role             VARCHAR(30)  NOT NULL,

    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    ultimo_login     TIMESTAMP    NULL

);