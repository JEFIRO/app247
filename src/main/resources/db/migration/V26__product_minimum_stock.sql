-- Estoque mínimo do Produto: um valor para o estoque central e outro que vale
-- para cada Condomínio. NULL significa "sem mínimo definido".
ALTER TABLE produto
    ADD COLUMN estoque_minimo_central DECIMAL(15,3) NULL,
    ADD COLUMN estoque_minimo_condominio DECIMAL(15,3) NULL,
    ADD CONSTRAINT ck_produto_estoque_minimo_central
        CHECK (estoque_minimo_central IS NULL OR estoque_minimo_central > 0),
    ADD CONSTRAINT ck_produto_estoque_minimo_condominio
        CHECK (estoque_minimo_condominio IS NULL OR estoque_minimo_condominio > 0);
