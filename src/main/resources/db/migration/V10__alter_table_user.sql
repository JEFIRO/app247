ALTER TABLE users
ADD CONSTRAINT fk_users_condominio
FOREIGN KEY (id_condominio)
REFERENCES condominio(id_condominio)
ON DELETE SET NULL;