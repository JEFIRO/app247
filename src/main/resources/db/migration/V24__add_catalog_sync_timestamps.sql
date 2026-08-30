ALTER TABLE estoque_condominio
    ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

ALTER TABLE produto
    MODIFY COLUMN create_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
    MODIFY COLUMN update_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6);

CREATE INDEX idx_estoque_condominio_sync
    ON estoque_condominio (condominio_id, updated_at);

CREATE INDEX idx_produto_sync
    ON produto (update_at);
