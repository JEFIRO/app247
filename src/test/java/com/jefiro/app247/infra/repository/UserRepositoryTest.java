package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.infra.service.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void getByCpf() {
        User user = createUser();

        User found = entityManager
                .createQuery("SELECT u FROM User u WHERE u.cpf = :cpf", User.class)
                .setParameter("cpf", "12345678901")
                .getSingleResult();

        assertNotNull(found);
        assertEquals(user.getCpf(), found.getCpf());
    }

    @Test
    @DisplayName("Pagar os Orders do usuário com sucesso")
    void findOrdersByUserIdSuccess() {
        User user = createUser();
        userRepository.findOrdersByUserId(user.getIdUser(), Pageable.ofSize(1));

    }

    private User createUser() {
        Empresa empresa = Empresa.builder()
                .razaoSocial("Empresa Teste")
                .nomeFantasia("Empresa Teste")
                .cnpj("12345678000199")
                .email("empresa@email.com")
                .tenantId("tenant-teste")
                .ativo(true)
                .dataCadastro(LocalDateTime.now())
                .build();
        entityManager.persist(empresa);
        User user = User.builder()
                .nome("Teste")
                .sobrenome("User")
                .email("teste@email.com")
                .senha("123456")
                .cpf("12345678901")
                .telefone("71999999999")
                .dataNascimento(LocalDate.of(2000, 1, 1))
                .empresa(empresa)
                .build();

        entityManager.persist(user);
        return user;
    }
}
