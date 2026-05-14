create table orders
(
    order_id    varchar(36) primary key,

    carrinho_id varchar(36)    not null,

    total       decimal(10, 2) not null,

    status      varchar(20) default 'PENDING',

    created_at  timestamp   default current_timestamp,

    constraint fk_order_carrinho
        foreign key (carrinho_id)
            references carrinho (carrinho_id)
);