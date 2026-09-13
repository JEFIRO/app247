CREATE TABLE payment_attempt (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    order_id CHAR(36) NOT NULL,
    attempt_number INT NOT NULL,
    provider VARCHAR(30) NOT NULL,
    channel VARCHAR(30) NOT NULL,
    payment_method VARCHAR(50) NULL,
    provider_payment_method VARCHAR(50) NULL,
    source VARCHAR(30) NOT NULL,
    status VARCHAR(40) NOT NULL,
    amount_requested DECIMAL(15,6) NOT NULL,
    amount_approved DECIMAL(15,6) NULL,
    installments INT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    external_reference VARCHAR(120) NOT NULL,
    provider_order_id VARCHAR(120) NULL,
    provider_payment_id VARCHAR(120) NULL,
    provider_terminal_id VARCHAR(120) NULL,
    provider_user_id VARCHAR(120) NULL,
    provider_event_version INT NULL,
    provider_event_at DATETIME(6) NULL,
    status_detail VARCHAR(160) NULL,
    nsu VARCHAR(120) NULL,
    authorization_code VARCHAR(120) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    approved_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_attempt_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT uk_payment_attempt_number UNIQUE (order_id, attempt_number),
    CONSTRAINT uk_payment_idempotency UNIQUE (provider, idempotency_key),
    CONSTRAINT uk_payment_external_reference UNIQUE (provider, external_reference),
    CONSTRAINT uk_payment_provider_order UNIQUE (provider, provider_order_id),
    CONSTRAINT uk_payment_provider_payment UNIQUE (provider, provider_payment_id),
    CONSTRAINT ck_payment_amount_requested CHECK (amount_requested >= 0),
    CONSTRAINT ck_payment_amount_approved CHECK (amount_approved IS NULL OR amount_approved >= 0),
    CONSTRAINT fk_payment_order_tenant FOREIGN KEY (order_id, empresa_id)
        REFERENCES orders (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_payment_order_status ON payment_attempt (order_id, status, attempt_number);
CREATE INDEX idx_payment_reconciliation ON payment_attempt (provider, status, updated_at);
CREATE TABLE payment_event (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    payment_attempt_id CHAR(36) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    status_anterior VARCHAR(40) NULL,
    status_novo VARCHAR(40) NOT NULL,
    provider_event_id VARCHAR(160) NULL,
    provider_version INT NULL,
    occurred_at DATETIME(6) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    metadata JSON NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_event_provider UNIQUE (provider, provider_event_id),
    CONSTRAINT fk_payment_event_attempt_tenant FOREIGN KEY (payment_attempt_id, empresa_id)
        REFERENCES payment_attempt (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_payment_event_attempt_occurred ON payment_event (payment_attempt_id, occurred_at);

CREATE TABLE mercado_pago_conta (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    access_token VARCHAR(1000) NOT NULL,
    refresh_token VARCHAR(1000) NOT NULL,
    public_key VARCHAR(500) NULL,
    mp_user_id VARCHAR(120) NOT NULL,
    token_type VARCHAR(40) NULL,
    scope TEXT NULL,
    live_mode BOOLEAN NULL,
    token_created_at DATETIME(6) NOT NULL,
    token_expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_mp_conta_empresa UNIQUE (empresa_id),
    CONSTRAINT uk_mp_conta_user UNIQUE (mp_user_id),
    CONSTRAINT fk_mp_conta_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id)
) ENGINE=InnoDB;

CREATE TABLE webhook_event (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NULL,
    payment_attempt_id CHAR(36) NULL,
    provider VARCHAR(30) NOT NULL,
    external_event_id VARCHAR(160) NOT NULL,
    action VARCHAR(100) NOT NULL,
    provider_version INT NULL,
    processing_status VARCHAR(30) NOT NULL,
    occurred_at DATETIME(6) NULL,
    received_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    payload JSON NULL,
    processing_error VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_webhook_provider_event UNIQUE (provider, external_event_id),
    CONSTRAINT ck_webhook_attempt_empresa CHECK (payment_attempt_id IS NULL OR empresa_id IS NOT NULL),
    CONSTRAINT fk_webhook_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT fk_webhook_attempt_tenant FOREIGN KEY (payment_attempt_id, empresa_id)
        REFERENCES payment_attempt (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_webhook_processing ON webhook_event (processing_status, received_at);
CREATE INDEX idx_webhook_empresa_received ON webhook_event (empresa_id, received_at);
