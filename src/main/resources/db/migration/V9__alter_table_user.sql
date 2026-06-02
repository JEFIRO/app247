ALTER TABLE users
ADD CONSTRAINT fk_users_condominio
FOREIGN KEY (condominio_id)
REFERENCES condominio(condominio_id)
ON DELETE SET NULL;