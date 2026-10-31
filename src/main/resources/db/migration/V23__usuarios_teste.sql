-- Usuarios de teste em producao, criados a pedido do responsavel pelo projeto.
-- Repositorio publico: a senha e aleatoria e so o hash BCrypt fica aqui.
-- Para remover: nova migration apagando estes ids, sem editar esta.

SET @agora = UTC_TIMESTAMP(6);
SET @senha = '$2a$12$u9FP.GvFTZlrrs8QNalrBu2kfk/PXvmGa09i1TnzCNGLxaVCpuCFy';
SET @empresa_id = 'a2470000-0000-4000-8000-00000000e001';
SET @condominio_1_id = 'a2470000-0000-4000-8000-00000000c001';
SET @condominio_2_id = 'a2470000-0000-4000-8000-00000000c002';
SET @condominio_3_id = 'a2470000-0000-4000-8000-00000000c003';

INSERT INTO empresa (id, tenant_id, razao_social, nome_fantasia, cnpj, email, ativo, created_at, updated_at)
VALUES (@empresa_id, 'a2470000-0000-4000-8000-00000000d001', 'Empresa Teste LTDA', 'Empresa Teste',
        '24700000000162', 'empresa@app247.test', 1, @agora, @agora);

INSERT INTO condominio (id, empresa_id, nome, ativo, created_at, updated_at)
VALUES (@condominio_1_id, @empresa_id, 'Condominio Teste 1', 1, @agora, @agora),
       (@condominio_2_id, @empresa_id, 'Condominio Teste 2', 1, @agora, @agora),
       (@condominio_3_id, @empresa_id, 'Condominio Teste 3', 1, @agora, @agora);

INSERT INTO users (id, empresa_id, condominio_id, nome, sobrenome, email, senha, cpf, role,
                   ativo, email_verificado, cpf_verified_at, created_at, updated_at)
VALUES ('a2470000-0000-4000-8000-00000000a001', @empresa_id, @condominio_1_id, 'Admin', 'Teste 1', 'admin1@app247.test', @senha, '24700000171', 'ADMIN', 1, 1, @agora, @agora, @agora),
       ('a2470000-0000-4000-8000-00000000a002', @empresa_id, @condominio_2_id, 'Admin', 'Teste 2', 'admin2@app247.test', @senha, '24700000252', 'ADMIN', 1, 1, @agora, @agora, @agora),
       ('a2470000-0000-4000-8000-00000000a003', @empresa_id, @condominio_3_id, 'Admin', 'Teste 3', 'admin3@app247.test', @senha, '24700000333', 'ADMIN', 1, 1, @agora, @agora, @agora),
       ('a2470000-0000-4000-8000-00000000b001', @empresa_id, @condominio_1_id, 'Usuario', 'Teste 1', 'user1@app247.test', @senha, '24700001143', 'USER', 1, 1, @agora, @agora, @agora),
       ('a2470000-0000-4000-8000-00000000b002', @empresa_id, @condominio_2_id, 'Usuario', 'Teste 2', 'user2@app247.test', @senha, '24700001224', 'USER', 1, 1, @agora, @agora, @agora),
       ('a2470000-0000-4000-8000-00000000b003', @empresa_id, @condominio_3_id, 'Usuario', 'Teste 3', 'user3@app247.test', @senha, '24700001305', 'USER', 1, 1, @agora, @agora, @agora);
