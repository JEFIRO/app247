package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.MercadoPagoConta;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class MercadoPagoContaRepositoryTest {
    @Autowired EntityManager entityManager;
    @Autowired OauthMercadoPagoRepository repository;

    @Test
    void empresasPossuemCredenciaisSeparadas() {
        Empresa empresaA = empresa("A", "33333333000133");
        Empresa empresaB = empresa("B", "44444444000144");
        repository.saveAndFlush(conta(empresaA, "token-a", "mp-a"));
        repository.saveAndFlush(conta(empresaB, "token-b", "mp-b"));

        assertEquals("token-a", repository.findByEmpresaId(empresaA.getId()).orElseThrow().getAccessToken());
        assertEquals("token-b", repository.findByEmpresaId(empresaB.getId()).orElseThrow().getAccessToken());
    }

    @Test
    void bancoImpedeDuasContasParaMesmaEmpresa() {
        Empresa empresa = empresa("U", "55555555000155");
        repository.saveAndFlush(conta(empresa, "token-1", "mp-1"));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(conta(empresa, "token-2", "mp-2")));
    }

    private MercadoPagoConta conta(Empresa empresa, String token, String mpUserId) {
        LocalDateTime agora = LocalDateTime.now();
        return MercadoPagoConta.builder().empresa(empresa).accessToken(token).refreshToken("refresh-" + token)
                .mpUserId(mpUserId).dataCriacao(agora).dataExpiracao(agora.plusHours(6)).build();
    }

    private Empresa empresa(String sufixo, String cnpj) {
        Empresa empresa = Empresa.builder().razaoSocial("Empresa " + sufixo).nomeFantasia("Empresa " + sufixo)
                .cnpj(cnpj).email("mp" + sufixo + "@email.com").tenantId("tenant-mp-" + sufixo)
                .ativo(true).dataCadastro(LocalDateTime.now()).build();
        entityManager.persist(empresa);
        return empresa;
    }
}
