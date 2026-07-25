create table produto
(
    id_produto       varchar(36) primary key,
    codigo           varchar(25) unique not null,
    nome             varchar(100)       not null,
    descricao        varchar(50),
    preco            decimal(10, 2)     not null,
    peso             decimal(10, 3),
    peso_tolerancia  decimal(10, 3),
    foto             varchar(255),
    categoria        varchar(55),
    quantidade       integer            not null,
    unidade_medida   varchar(50),
    grupo_tributario varchar(36)        not null,
    ncm              varchar(36)        not null,
    create_at        timestamp default current_timestamp,
    update_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    status           boolean   default true,

    empresa_id       varchar(36)        NOT NULL,
    CONSTRAINT fk_produto_empresa
        FOREIGN KEY (empresa_id)
            REFERENCES empresa (id)

)