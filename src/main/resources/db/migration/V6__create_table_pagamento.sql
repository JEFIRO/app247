create table pagamento
(
    pagamento_id       varchar(36) primary key,

    order_id           varchar(36)    not null unique,

    valor              decimal(10, 2) not null,

    tipo               varchar(30)    not null,

    status             varchar(30) default 'PENDING',

    transaction_id     varchar(255),

    nsu                varchar(255),

    authorization_code varchar(255),

    created_at         timestamp   default current_timestamp,

    paid_at            timestamp,

    constraint fk_pagamento_order
        foreign key (order_id)
            references orders (order_id)
);
