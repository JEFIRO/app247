ALTER TABLE produto
    ADD CONSTRAINT fk_produto_grupo_tributario
        FOREIGN KEY (grupo_tributario)
            REFERENCES grupo_tributario (id_tributacao);