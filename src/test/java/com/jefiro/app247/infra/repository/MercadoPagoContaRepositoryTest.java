package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.MercadoPagoConta;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class MercadoPagoContaRepositoryTest {
    @Autowired EntityManager entityManager;
    @Autowired OauthMercadoPagoRepository repository;
    @Autowired MercadoPagoContaAtivaRepository ativaRepository;

    @Test
    void empresasPossuemCredenciaisSeparadas() {
        Empresa empresaA = empresa("A", "33333333000133");
        Empresa empresaB = empresa("B", "44444444000144");
        MercadoPagoConta contaA = repository.saveAndFlush(conta(empresaA, "token-a", "mp-a"));
        MercadoPagoConta contaB = repository.saveAndFlush(conta(empresaB, "token-b", "mp-b"));
        ativaRepository.saveAndFlush(new com.jefiro.app247.domain.model.MercadoPagoContaAtiva(contaA));
        ativaRepository.saveAndFlush(new com.jefiro.app247.domain.model.MercadoPagoContaAtiva(contaB));

        assertEquals("token-a", ativaRepository.findByEmpresaId(empresaA.getId()).orElseThrow()
                .getConta().getAccessToken());
        assertEquals("token-b", ativaRepository.findByEmpresaId(empresaB.getId()).orElseThrow()
                .getConta().getAccessToken());
    }

    @Test
    void bancoImpedeDuasContasAtivasParaMesmaEmpresa() {
        Empresa empresa = empresa("U", "55555555000155");
        MercadoPagoConta primeira = repository.saveAndFlush(conta(empresa, "token-1", "mp-1"));
        MercadoPagoConta segunda = repository.saveAndFlush(conta(empresa, "token-2", "mp-2"));
        ativaRepository.saveAndFlush(new com.jefiro.app247.domain.model.MercadoPagoContaAtiva(primeira));

        assertThrows(DataIntegrityViolationException.class,
                () -> ativaRepository.saveAndFlush(
                        new com.jefiro.app247.domain.model.MercadoPagoContaAtiva(segunda)));
    }

    @Test
    void bancoImpedeMesmaContaRealAtivaEmDuasEmpresas() {
        Empresa empresaA = empresa("C", "66666666000166");
        Empresa empresaB = empresa("D", "77777777000177");
        MercadoPagoConta contaA = repository.saveAndFlush(conta(empresaA, "token-a", "mp-shared"));
        MercadoPagoConta contaB = repository.saveAndFlush(conta(empresaB, "token-b", "mp-shared"));
        ativaRepository.saveAndFlush(new com.jefiro.app247.domain.model.MercadoPagoContaAtiva(contaA));

        assertThrows(DataIntegrityViolationException.class,
                () -> ativaRepository.saveAndFlush(
                        new com.jefiro.app247.domain.model.MercadoPagoContaAtiva(contaB)));
    }

    @Test
    void unlinkMantemHistoricoELiberaContaRealParaOutraEmpresa() {
        Empresa empresaA = empresa("E", "88888888000188");
        Empresa empresaB = empresa("F", "99999999000199");
        MercadoPagoConta contaA = repository.saveAndFlush(conta(empresaA, "token-a", "mp-reusable"));
        var leaseA = ativaRepository.saveAndFlush(
                new com.jefiro.app247.domain.model.MercadoPagoContaAtiva(contaA));

        ativaRepository.delete(leaseA);
        ativaRepository.flush();
        contaA.unlink("USER_UNLINK");
        repository.saveAndFlush(contaA);

        MercadoPagoConta contaB = repository.saveAndFlush(conta(empresaB, "token-b", "mp-reusable"));
        ativaRepository.saveAndFlush(new com.jefiro.app247.domain.model.MercadoPagoContaAtiva(contaB));

        assertEquals(com.jefiro.app247.domain.model.enum_type.MercadoPagoAccountBindingStatus.UNLINKED,
                repository.findById(contaA.getIdMercadoConta()).orElseThrow().getStatus());
        assertNull(repository.findById(contaA.getIdMercadoConta()).orElseThrow().getAccessToken());
        assertEquals(empresaB.getId(), ativaRepository.findWithBindingByMpUserId("mp-reusable")
                .orElseThrow().getEmpresa().getId());
        assertEquals(2, repository.findAll().stream()
                .filter(conta -> conta.getMpUserId().equals("mp-reusable")).count());
    }

    private MercadoPagoConta conta(Empresa empresa, String token, String mpUserId) {
        Instant agora = Instant.now();
        return MercadoPagoConta.builder().empresa(empresa).accessToken(token).refreshToken("refresh-" + token)
                .mpUserId(mpUserId).dataCriacao(agora).dataExpiracao(agora.plusSeconds(6 * 3600)).build();
    }

    private Empresa empresa(String sufixo, String cnpj) {
        Empresa empresa = Empresa.builder().razaoSocial("Empresa " + sufixo).nomeFantasia("Empresa " + sufixo)
                .cnpj(cnpj).email("mp" + sufixo + "@email.com").tenantId("tenant-mp-" + sufixo)
                .ativo(true).dataCadastro(Instant.now()).build();
        entityManager.persist(empresa);
        return empresa;
    }
}
