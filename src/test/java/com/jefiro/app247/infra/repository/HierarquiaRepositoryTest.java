package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.Endereco;
import com.jefiro.app247.domain.model.dto.CondominioRequest;
import com.jefiro.app247.domain.model.dto.EnderecoDTO;
import com.jefiro.app247.domain.model.dto.TerminalRequest;
import com.jefiro.app247.domain.model.terminal.Terminal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class HierarquiaRepositoryTest {
    @Autowired EntityManager entityManager;
    @Autowired CondominioRepository condominioRepository;
    @Autowired TerminalRepository terminalRepository;

    @Test
    void carregaEmpresaComVariosCondominiosETerminaisSemEmpresaDiretaNoTerminal() {
        Empresa empresaA = empresa("A", "11111111000111");
        Empresa empresaB = empresa("B", "22222222000122");
        Condominio alpha = condominio("Alpha", empresaA);
        Condominio beta = condominio("Beta", empresaA);
        Condominio outroTenant = condominio("Outro", empresaB);
        terminal("Recepção", "SER-1", alpha);
        terminal("Bloco A", "SER-2", alpha);
        Terminal terminalBeta = terminal("Recepção Beta", "SER-3", beta);
        entityManager.flush();
        entityManager.clear();

        assertEquals(2, condominioRepository.findAllByEmpresaIdOrderByNome(empresaA.getId()).size());
        assertEquals(2, terminalRepository
                .findAllByCondominioIdCondominioAndCondominioEmpresaIdOrderByNome(alpha.getIdCondominio(), empresaA.getId())
                .size());
        assertTrue(terminalRepository
                .findByIdTerminalAndCondominioEmpresaId(terminalBeta.getIdTerminal(), empresaA.getId()).isPresent());
        assertTrue(condominioRepository
                .findByIdCondominioAndEmpresaId(outroTenant.getIdCondominio(), empresaA.getId()).isEmpty());
        assertTrue(terminalRepository
                .findByIdTerminalAndCondominioEmpresaId(terminalBeta.getIdTerminal(), empresaB.getId()).isEmpty());
    }

    @Test
    void bancoImpedeMesmaMaquininhaMercadoPagoEmDoisTerminais() {
        Empresa empresa = empresa("MP", "66666666000166");
        Condominio condominio = condominio("MP", empresa);
        Terminal primeiro = terminal("Primeiro", "SER-MP-1", condominio);
        primeiro.setMercadoPagoTerminalId("PAX-UNICA");
        entityManager.flush();

        Terminal segundo = terminal("Segundo", "SER-MP-2", condominio);
        segundo.setMercadoPagoTerminalId("PAX-UNICA");
        assertThrows(jakarta.persistence.PersistenceException.class, entityManager::flush);
    }

    private Empresa empresa(String sufixo, String cnpj) {
        Empresa empresa = Empresa.builder().razaoSocial("Empresa " + sufixo).nomeFantasia("Empresa " + sufixo)
                .cnpj(cnpj).email("empresa" + sufixo + "@email.com").tenantId("tenant-" + sufixo)
                .ativo(true).dataCadastro(LocalDateTime.now()).build();
        entityManager.persist(empresa);
        return empresa;
    }

    private Condominio condominio(String nome, Empresa empresa) {
        EnderecoDTO enderecoDTO = new EnderecoDTO("Rua " + nome, "10", null, "Centro", "Salvador", "BA", "40000000");
        Endereco endereco = new Endereco(enderecoDTO);
        endereco.setEmpresa(empresa);
        Condominio condominio = new Condominio(new CondominioRequest(nome, null, enderecoDTO), endereco);
        condominio.setEmpresa(empresa);
        entityManager.persist(condominio);
        return condominio;
    }

    private Terminal terminal(String nome, String serial, Condominio condominio) {
        Terminal terminal = new Terminal(new TerminalRequest(nome, serial, null, null));
        terminal.setCondominio(condominio);
        entityManager.persist(terminal);
        return terminal;
    }
}
