create table pagamento
(
    id_pagamento       varchar(36) primary key,

    id_order           varchar(36)    not null unique,

    valor              decimal(10, 2) not null,

    tipo               varchar(30)    not null,

    status             varchar(30) default 'PENDING',

    id_transaction     varchar(255),

    nsu                varchar(255),

    authorization_code varchar(255),

    created_at         timestamp   default current_timestamp,

    paid_at            timestamp,
    updated_at         timestamp,
    payment_method_id  varchar(100),
    status_detail      varchar(100),

    empresa_id         varchar(36)    NOT NULL,
    CONSTRAINT fk_pagamento_empresa
        FOREIGN KEY (empresa_id)
            REFERENCES empresa (id),
    constraint fk_pagamento_order
        foreign key (id_order)
            references orders (id_order)
);
