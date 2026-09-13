ALTER TABLE empresa
    ADD COLUMN closed_at DATETIME(6) NULL AFTER ativo;

-- No MySQL, a unique antiga de empresa_id também é usada como índice da FK.
-- Soltar e recriar a FK permite remover a regra 1:1 sem depender da escolha
-- interna de índices feita pelo engine.
ALTER TABLE mercado_pago_conta
    DROP FOREIGN KEY fk_mp_conta_empresa;

ALTER TABLE mercado_pago_conta
    DROP INDEX uk_mp_conta_empresa,
    DROP INDEX uk_mp_conta_user,
    MODIFY COLUMN access_token VARCHAR(1000) NULL,
    MODIFY COLUMN refresh_token VARCHAR(1000) NULL,
    MODIFY COLUMN token_created_at DATETIME(6) NULL,
    MODIFY COLUMN token_expires_at DATETIME(6) NULL,
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' AFTER mp_user_id,
    ADD COLUMN linked_at DATETIME(6) NULL AFTER status,
    ADD COLUMN unlinked_at DATETIME(6) NULL AFTER linked_at,
    ADD COLUMN unlink_reason VARCHAR(80) NULL AFTER unlinked_at,
    ADD COLUMN last_error VARCHAR(300) NULL AFTER unlink_reason;

UPDATE mercado_pago_conta
SET linked_at = COALESCE(token_created_at, created_at, UTC_TIMESTAMP(6));

ALTER TABLE mercado_pago_conta
    MODIFY COLUMN linked_at DATETIME(6) NOT NULL,
    ADD CONSTRAINT uk_mp_conta_id_empresa UNIQUE (id, empresa_id),
    ADD CONSTRAINT uk_mp_conta_id_empresa_user UNIQUE (id, empresa_id, mp_user_id),
    ADD CONSTRAINT fk_mp_conta_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id);

CREATE TABLE mercado_pago_conta_ativa (
    mp_user_id VARCHAR(120) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    mercado_pago_conta_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (mp_user_id),
    CONSTRAINT uk_mp_conta_ativa_empresa UNIQUE (empresa_id),
    CONSTRAINT uk_mp_conta_ativa_binding UNIQUE (mercado_pago_conta_id),
    CONSTRAINT fk_mp_conta_ativa_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT fk_mp_conta_ativa_binding FOREIGN KEY
        (mercado_pago_conta_id, empresa_id, mp_user_id)
        REFERENCES mercado_pago_conta (id, empresa_id, mp_user_id)
) ENGINE=InnoDB;

INSERT INTO mercado_pago_conta_ativa
    (mp_user_id, empresa_id, mercado_pago_conta_id, created_at)
SELECT mp_user_id, empresa_id, id, linked_at
FROM mercado_pago_conta
WHERE status = 'ACTIVE';

ALTER TABLE terminal
    ADD COLUMN lifecycle_state VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' AFTER status,
    ADD COLUMN reset_requested_at DATETIME(6) NULL AFTER lifecycle_state,
    ADD COLUMN reset_started_at DATETIME(6) NULL AFTER reset_requested_at,
    ADD COLUMN reset_completed_at DATETIME(6) NULL AFTER reset_started_at,
    ADD COLUMN reset_reason VARCHAR(80) NULL AFTER reset_completed_at;

CREATE TABLE terminal_point_binding (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    terminal_id CHAR(36) NOT NULL,
    mercado_pago_conta_id CHAR(36) NOT NULL,
    mercado_pago_terminal_id VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    linked_at DATETIME(6) NOT NULL,
    unlinked_at DATETIME(6) NULL,
    unlink_reason VARCHAR(80) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_terminal_point_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT fk_terminal_point_terminal FOREIGN KEY (terminal_id) REFERENCES terminal (id),
    CONSTRAINT fk_terminal_point_account_binding FOREIGN KEY
        (mercado_pago_conta_id, empresa_id)
        REFERENCES mercado_pago_conta (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_terminal_point_terminal_status
    ON terminal_point_binding (terminal_id, status, linked_at);
CREATE INDEX idx_terminal_point_account_status
    ON terminal_point_binding (mercado_pago_conta_id, status);
CREATE INDEX idx_mp_conta_empresa_status
    ON mercado_pago_conta (empresa_id, status, linked_at);

INSERT INTO terminal_point_binding
    (id, empresa_id, terminal_id, mercado_pago_conta_id, mercado_pago_terminal_id,
     status, linked_at, created_at, updated_at)
SELECT UUID(), c.empresa_id, t.id, a.mercado_pago_conta_id, t.mercado_pago_terminal_id,
       'ACTIVE', a.created_at, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM terminal t
JOIN condominio c ON c.id = t.condominio_id
JOIN mercado_pago_conta_ativa a ON a.empresa_id = c.empresa_id
WHERE t.mercado_pago_terminal_id IS NOT NULL;

UPDATE terminal t
JOIN condominio c ON c.id = t.condominio_id
LEFT JOIN mercado_pago_conta_ativa a ON a.empresa_id = c.empresa_id
SET t.mercado_pago_terminal_id = NULL
WHERE t.mercado_pago_terminal_id IS NOT NULL
  AND a.empresa_id IS NULL;
