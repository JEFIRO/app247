alter table condominio
add  CONSTRAINT fk_condominio_endereco
        FOREIGN KEY (endereco_id)
        REFERENCES endereco(id);