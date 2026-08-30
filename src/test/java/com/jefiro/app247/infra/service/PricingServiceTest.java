package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.enum_type.AbrangenciaPromocao;
import com.jefiro.app247.domain.model.enum_type.TipoPromocao;
import com.jefiro.app247.infra.repository.EstoqueCondominioRepository;
import com.jefiro.app247.infra.repository.PromocaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {
    @Mock PromocaoRepository promocaoRepository;
    @Mock EstoqueCondominioRepository estoqueRepository;
    @InjectMocks PricingService service;

    Empresa empresa;
    Condominio condominio;
    Produto produto;
    LocalDateTime agora;

    @BeforeEach
    void setup() {
        empresa = Empresa.builder().id("empresa-a").build();
        condominio = new Condominio();
        condominio.setIdCondominio("cond-a");
        condominio.setEmpresa(empresa);
        produto = new Produto();
        produto.setIdProduto("produto-a");
        produto.setEmpresa(empresa);
        produto.setPreco(new BigDecimal("10.990000"));
        produto.setStatus(true);
        EstoqueCondominio estoque = new EstoqueCondominio();
        estoque.setProduto(produto);
        estoque.setCondominio(condominio);
        estoque.setAtivo(true);
        estoque.setQuantidade(new BigDecimal("-5.000"));
        when(estoqueRepository.findByCondominioIdCondominioAndProdutoIdProdutoAndAtivoTrue(
                "cond-a", "produto-a")).thenReturn(Optional.of(estoque));
        agora = LocalDateTime.of(2026, 8, 30, 12, 0);
    }

    @Test
    void calculaPercentualExato() {
        when(promocaoRepository.findAplicaveis(anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of(promocao("p1", AbrangenciaPromocao.EMPRESA,
                        TipoPromocao.PERCENTUAL, "7.500000", 0)));

        var preco = service.calcular(produto, condominio, agora);

        assertEquals(new BigDecimal("10.165750"), preco.precoCalculado());
        assertEquals(new BigDecimal("0.824250"), preco.descontoCalculado());
    }

    @Test
    void cobreDescontoFixoEPrecoFixo() {
        when(promocaoRepository.findAplicaveis(anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of(
                        promocao("desconto", AbrangenciaPromocao.EMPRESA,
                                TipoPromocao.DESCONTO_FIXO, "2.500000", 0),
                        promocao("fixo", AbrangenciaPromocao.EMPRESA,
                                TipoPromocao.PRECO_FIXO, "7.990000", 0)));

        var preco = service.calcular(produto, condominio, agora);

        assertEquals(new BigDecimal("7.990000"), preco.precoCalculado());
        assertEquals("fixo", preco.promocao().getIdPromocao());
    }

    @Test
    void promocoesNaoAcumulamEEscolhemMenorPreco() {
        when(promocaoRepository.findAplicaveis(anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of(
                        promocao("empresa", AbrangenciaPromocao.EMPRESA,
                                TipoPromocao.PERCENTUAL, "10.000000", 100),
                        promocao("condominio", AbrangenciaPromocao.CONDOMINIO,
                                TipoPromocao.PERCENTUAL, "20.000000", 0)));

        var preco = service.calcular(produto, condominio, agora);

        assertEquals(new BigDecimal("8.792000"), preco.precoCalculado());
        assertEquals("condominio", preco.promocao().getIdPromocao());
    }

    @Test
    void desempataPorCondominioDepoisPrioridadeEId() {
        Promocao empresaAlta = promocao("z", AbrangenciaPromocao.EMPRESA,
                TipoPromocao.PRECO_FIXO, "8.000000", 100);
        Promocao condominioBaixa = promocao("y", AbrangenciaPromocao.CONDOMINIO,
                TipoPromocao.PRECO_FIXO, "8.000000", 0);
        when(promocaoRepository.findAplicaveis(anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of(empresaAlta, condominioBaixa));
        assertEquals("y", service.calcular(produto, condominio, agora).promocao().getIdPromocao());

        Promocao prioridade1 = promocao("z", AbrangenciaPromocao.EMPRESA,
                TipoPromocao.PRECO_FIXO, "8.000000", 1);
        Promocao prioridade2 = promocao("x", AbrangenciaPromocao.EMPRESA,
                TipoPromocao.PRECO_FIXO, "8.000000", 2);
        when(promocaoRepository.findAplicaveis(anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of(prioridade1, prioridade2));
        assertEquals("x", service.calcular(produto, condominio, agora).promocao().getIdPromocao());
    }

    private Promocao promocao(String id, AbrangenciaPromocao abrangencia,
                              TipoPromocao tipo, String valor, int prioridade) {
        Promocao promocao = new Promocao();
        promocao.setIdPromocao(id);
        promocao.setAbrangencia(abrangencia);
        promocao.setTipo(tipo);
        promocao.setValor(new BigDecimal(valor));
        promocao.setPrioridade(prioridade);
        return promocao;
    }
}
