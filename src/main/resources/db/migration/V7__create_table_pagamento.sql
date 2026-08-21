CREATE TABLE pagamento
(
    id_pagamento       VARCHAR(36) PRIMARY KEY,

    valor              DECIMAL(10, 2) NOT NULL,

    tipo               VARCHAR(16),

    status             VARCHAR(30) DEFAULT 'PENDING',

    id_transaction     VARCHAR(255),

    nsu                VARCHAR(255),

    authorization_code VARCHAR(255),

    created_at         TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,

    paid_at            TIMESTAMP,

    updated_at         TIMESTAMP,

    payment_method_id  VARCHAR(100),

    status_detail      VARCHAR(100),

    empresa_id         VARCHAR(36)    NOT NULL,

    source_paiment     varchar(16)    not null,
    CONSTRAINT fk_pagamento_empresa
        FOREIGN KEY (empresa_id)
            REFERENCES empresa (id)
);