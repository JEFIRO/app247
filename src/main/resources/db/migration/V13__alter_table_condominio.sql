alter table condominio
add  CONSTRAINT fk_condominio_endereco
        FOREIGN KEY (id_endereco)
        REFERENCES endereco(id_endereco);