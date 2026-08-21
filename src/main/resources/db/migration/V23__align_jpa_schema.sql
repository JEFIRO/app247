ALTER TABLE orders
    ADD COLUMN origin_request VARCHAR(30) NULL,
    MODIFY COLUMN id_terminal VARCHAR(36) NULL,
    MODIFY COLUMN mp_user_id VARCHAR(255) NULL;

ALTER TABLE pagamento
    CHANGE COLUMN id_transaction transaction_id VARCHAR(255) NULL,
    ADD COLUMN installments INT NULL;

ALTER TABLE item
    MODIFY COLUMN foto VARCHAR(255) NULL;

ALTER TABLE produto
    MODIFY COLUMN descricao VARCHAR(255) NULL;

ALTER TABLE grupo_tributario
    MODIFY COLUMN id_tributacao VARCHAR(36) NOT NULL;
