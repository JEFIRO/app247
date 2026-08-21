ALTER TABLE terminal
    ADD COLUMN mercado_pago_terminal_id VARCHAR(255) NULL;

UPDATE terminal t
    INNER JOIN condominio c
        ON c.id_condominio = t.condominio_id
    INNER JOIN (
        SELECT empresa_id, MAX(terminal_id) AS mercado_pago_terminal_id
        FROM mercado_pago_conta
        WHERE terminal_id IS NOT NULL AND terminal_id <> ''
        GROUP BY empresa_id
        HAVING COUNT(*) = 1
    ) conta
        ON conta.empresa_id = c.empresa_id
    INNER JOIN (
        SELECT c2.empresa_id, MIN(t2.id_terminal) AS id_terminal
        FROM terminal t2
            INNER JOIN condominio c2
                ON c2.id_condominio = t2.condominio_id
        GROUP BY c2.empresa_id
        HAVING COUNT(*) = 1
    ) terminal_unico
        ON terminal_unico.empresa_id = c.empresa_id
        AND terminal_unico.id_terminal = t.id_terminal
SET t.mercado_pago_terminal_id = conta.mercado_pago_terminal_id;

ALTER TABLE mercado_pago_conta
    DROP COLUMN terminal_id,
    ADD CONSTRAINT uk_mercado_pago_conta_empresa UNIQUE (empresa_id);

ALTER TABLE terminal
    ADD CONSTRAINT uk_terminal_mercado_pago_terminal UNIQUE (mercado_pago_terminal_id);
