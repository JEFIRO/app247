-- Produto marcado para ficar disponível em todos os Condomínios da Empresa,
-- inclusive nos que forem criados depois.
ALTER TABLE produto
    ADD COLUMN disponivel_todos_condominios BOOLEAN NOT NULL DEFAULT FALSE;
