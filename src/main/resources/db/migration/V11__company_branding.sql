ALTER TABLE empresa
    ADD COLUMN nome_exibicao VARCHAR(150) NULL AFTER estado,
    ADD COLUMN logo_url VARCHAR(500) NULL AFTER nome_exibicao,
    ADD COLUMN logo_dark_url VARCHAR(500) NULL AFTER logo_url,
    ADD COLUMN cor_principal CHAR(7) NOT NULL DEFAULT '#169DFF' AFTER logo_dark_url,
    ADD COLUMN cor_secundaria CHAR(7) NOT NULL DEFAULT '#62C8FF' AFTER cor_principal,
    ADD COLUMN cor_destaque CHAR(7) NOT NULL DEFAULT '#00D084' AFTER cor_secundaria;
