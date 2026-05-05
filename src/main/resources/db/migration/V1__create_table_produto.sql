create table produto
(
    id              integer auto_increment primary key,
    codigo          varchar(25),
    nome            varchar(100),
    descricao       varchar(50),
    preco           decimal(10, 2),
    peso            decimal(10, 2),
    peso_tolerancia decimal(10, 2),
    foto            varchar(255),
    categoria       varchar(55),
    quantidade      integer,
    unidade_medida  varchar(50),
    create_at       timestamp default current_timestamp,
    update_at       timestamp default current_timestamp,
    status          boolean   default true
)