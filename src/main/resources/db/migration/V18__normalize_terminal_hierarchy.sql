ALTER TABLE terminal
    DROP FOREIGN KEY fk_terminal_empresa,
    DROP FOREIGN KEY fk_terminal_condominio;

ALTER TABLE terminal
    MODIFY COLUMN id_terminal VARCHAR(36) NOT NULL,
    CHANGE COLUMN id_condominio condominio_id VARCHAR(36) NOT NULL,
    DROP COLUMN empresa_id;

ALTER TABLE terminal
    ADD CONSTRAINT fk_terminal_condominio
        FOREIGN KEY (condominio_id)
            REFERENCES condominio (id_condominio)
            ON DELETE CASCADE;
