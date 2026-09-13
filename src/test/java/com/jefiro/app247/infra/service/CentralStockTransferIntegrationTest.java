package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.dto.estoque.*;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.ProdutoCatalogChangeReason;
import com.jefiro.app247.domain.model.enum_type.StatusTransferenciaEstoque;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import com.jefiro.app247.infra.event.ProdutoCatalogChangedEvent;
import com.jefiro.app247.infra.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.task.scheduling.enabled=false","payment.reconciliation.startup.enabled=false"})
@ActiveProfiles("test")
@RecordApplicationEvents
class CentralStockTransferIntegrationTest {
    @Autowired EstoqueEmpresaService estoqueEmpresaService;
    @Autowired TransferenciaEstoqueService transferenciaService;
    @Autowired PlanogramaService planogramaService;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired CondominioRepository condominioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired EstoqueEmpresaRepository estoqueEmpresaRepository;
    @Autowired EstoqueCondominioRepository estoqueCondominioRepository;
    @Autowired MovimentacaoEstoqueRepository movimentoRepository;
    @Autowired ApplicationEvents applicationEvents;

    @AfterEach void clear(){EmpresaContext.clear();}

    @Test
    void duasEntradasSomamCemMaisVinte(){
        Fixture f=fixture();EmpresaContext.set(f.empresa.getId());
        estoqueEmpresaService.entrada(f.produto.getIdProduto(),new BigDecimal("100.000"),"inventário inicial");
        estoqueEmpresaService.entrada(f.produto.getIdProduto(),new BigDecimal("20.000"),"compra");
        assertThat(central(f).getQuantidade()).isEqualByComparingTo("120.000");
        assertThat(applicationEvents.stream(ProdutoCatalogChangedEvent.class)).isEmpty();
    }

    @Test
    void ajusteDeCemParaNoventaESeteRegistraDeltaMenosTres(){
        Fixture f=fixture();EmpresaContext.set(f.empresa.getId());
        estoqueEmpresaService.entrada(f.produto.getIdProduto(),new BigDecimal("100.000"),"inventário inicial");
        estoqueEmpresaService.ajustar(f.produto.getIdProduto(),new BigDecimal("97.000"),"contagem física");
        assertThat(central(f).getQuantidade()).isEqualByComparingTo("97.000");
        assertThat(movimentoRepository.findAllByEstoqueEmpresaEmpresaIdOrderByCreatedAtDesc(f.empresa.getId()).get(0).getQuantidade())
                .isEqualByComparingTo("-3.000");
    }

    @Test
    void transferenciaConfirmaAtomicamenteNotificaDestinoEContinuaIdempotente(){
        Fixture f=fixture();EmpresaContext.set(f.empresa.getId());
        estoqueEmpresaService.entrada(f.produto.getIdProduto(),new BigDecimal("100.000"),"inventário inicial");
        EstoqueCondominio destino=new EstoqueCondominio();destino.setEmpresa(f.empresa);destino.setCondominio(f.condominio);
        destino.setProduto(f.produto);destino.setQuantidade(new BigDecimal("30.000"));destino.setAtivo(true);
        estoqueCondominioRepository.saveAndFlush(destino);
        var transferencia=transferenciaService.criar(request(f,"20.000"));
        var concluida=transferenciaService.confirmar(transferencia.id());
        assertThat(concluida.status()).isEqualTo(StatusTransferenciaEstoque.CONCLUIDA);
        assertThat(central(f).getQuantidade()).isEqualByComparingTo("80.000");
        assertThat(condominio(f).getQuantidade()).isEqualByComparingTo("50.000");
        assertThat(concluida.movimentacoes()).hasSize(2);
        assertThat(applicationEvents.stream(ProdutoCatalogChangedEvent.class)).anyMatch(event ->
                event.productId().equals(f.produto.getIdProduto())
                        && event.reason()== ProdutoCatalogChangeReason.STOCK_TRANSFER_COMPLETED
                        && event.condominiumIds().equals(java.util.Set.of(f.condominio.getIdCondominio())));

        transferenciaService.confirmar(transferencia.id());
        assertThat(central(f).getQuantidade()).isEqualByComparingTo("80.000");
        assertThat(condominio(f).getQuantidade()).isEqualByComparingTo("50.000");

        var cancelavel=transferenciaService.criar(request(f,"5.000"));
        assertThat(transferenciaService.cancelar(cancelavel.id()).status()).isEqualTo(StatusTransferenciaEstoque.CANCELADA);
        assertThat(central(f).getQuantidade()).isEqualByComparingTo("80.000");
    }

    @Test
    void falhaAoCreditarDestinoReverteDebitoDaOrigem(){
        Fixture f=fixture();EmpresaContext.set(f.empresa.getId());
        estoqueEmpresaService.entrada(f.produto.getIdProduto(),new BigDecimal("100.000"),"inicial");
        EstoqueCondominio destino=new EstoqueCondominio();destino.setEmpresa(f.empresa);destino.setCondominio(f.condominio);
        destino.setProduto(f.produto);destino.setQuantidade(new BigDecimal("999999999999.999"));destino.setAtivo(true);
        estoqueCondominioRepository.saveAndFlush(destino);
        var transferencia=transferenciaService.criar(request(f,"20.000"));

        assertThatThrownBy(()->transferenciaService.confirmar(transferencia.id())).isInstanceOf(RuntimeException.class);
        assertThat(central(f).getQuantidade()).isEqualByComparingTo("100.000");
        assertThat(condominio(f).getQuantidade()).isEqualByComparingTo("999999999999.999");
        assertThat(transferenciaService.buscar(transferencia.id()).status()).isEqualTo(StatusTransferenciaEstoque.RASCUNHO);
    }

    @Test
    void planogramaEhOpcionalEProdutoPodeSerMovidoERemovidoSemAlterarSaldo(){
        Fixture f=fixture();EmpresaContext.set(f.empresa.getId());
        estoqueEmpresaService.entrada(f.produto.getIdProduto(),new BigDecimal("100.000"),"inicial");
        var planograma=planogramaService.criar(new PlanogramaRequest("Central",null,true));
        var comA=planogramaService.adicionarPosicao(planograma.id(),new PlanogramaPosicaoRequest(
                "Bebidas","A","01",null,"03","02",1,null,null,null,null));
        String posicaoA=comA.posicoes().get(0).id();
        var comB=planogramaService.adicionarPosicao(planograma.id(),new PlanogramaPosicaoRequest(
                "Bebidas","B","01",null,"01","01",2,null,null,null,null));
        String posicaoB=comB.posicoes().get(1).id();
        var posicionado=planogramaService.adicionarProduto(posicaoA,new PlanogramaProdutoRequest(
                f.produto.getIdProduto(),2,new BigDecimal("24.000"),new BigDecimal("20.000"),new BigDecimal("5.000"),true));
        String vinculo=posicionado.posicoes().get(0).produtos().get(0).id();
        var movido=planogramaService.moverProduto(vinculo,posicaoB);
        assertThat(movido.posicoes().get(1).produtos()).hasSize(1);
        var removido=planogramaService.removerProduto(vinculo);
        assertThat(removido.posicoes().get(1).produtos().get(0).ativo()).isFalse();
        assertThat(central(f).getQuantidade()).isEqualByComparingTo("100.000");
    }

    @Test
    void transferenciaBloqueiaCondominioDeOutroTenant(){
        Fixture f=fixture();Fixture outro=fixture();EmpresaContext.set(f.empresa.getId());
        var request=new TransferenciaEstoqueRequest(outro.condominio.getIdCondominio(),null,
                List.of(new TransferenciaEstoqueRequest.Item(f.produto.getIdProduto(),BigDecimal.ONE)));
        assertThatThrownBy(()->transferenciaService.criar(request)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void transferenciaBloqueiaProdutoDeOutroTenant(){
        Fixture f=fixture();Fixture outro=fixture();EmpresaContext.set(f.empresa.getId());
        var request=new TransferenciaEstoqueRequest(f.condominio.getIdCondominio(),null,
                List.of(new TransferenciaEstoqueRequest.Item(outro.produto.getIdProduto(),BigDecimal.ONE)));
        assertThatThrownBy(()->transferenciaService.criar(request)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void transferenciaCriaEstoqueDestinoAtivoQuandoAssociacaoNaoExiste(){
        Fixture f=fixture();EmpresaContext.set(f.empresa.getId());
        estoqueEmpresaService.entrada(f.produto.getIdProduto(),new BigDecimal("20.000"),"inicial");
        var transferencia=transferenciaService.criar(request(f,"20.000"));

        transferenciaService.confirmar(transferencia.id());

        assertThat(central(f).getQuantidade()).isEqualByComparingTo("0.000");
        assertThat(condominio(f).getQuantidade()).isEqualByComparingTo("20.000");
        assertThat(condominio(f).getAtivo()).isTrue();
    }

    private TransferenciaEstoqueRequest request(Fixture f,String quantidade){return new TransferenciaEstoqueRequest(
            f.condominio.getIdCondominio(),"reposição",List.of(new TransferenciaEstoqueRequest.Item(
            f.produto.getIdProduto(),new BigDecimal(quantidade))));}
    private EstoqueEmpresa central(Fixture f){return estoqueEmpresaRepository.findByEmpresaIdAndProdutoIdProduto(
            f.empresa.getId(),f.produto.getIdProduto()).orElseThrow();}
    private EstoqueCondominio condominio(Fixture f){return estoqueCondominioRepository
            .findByCondominioIdCondominioAndProdutoIdProduto(f.condominio.getIdCondominio(),f.produto.getIdProduto()).orElseThrow();}
    private Fixture fixture(){
        String suffix=UUID.randomUUID().toString().substring(0,8);
        Empresa empresa=Empresa.builder().razaoSocial("Empresa "+suffix).nomeFantasia("Empresa "+suffix)
                .cnpj("CNPJ-"+suffix).email(suffix+"@test.local").ativo(true).build();
        empresa=empresaRepository.saveAndFlush(empresa);
        Condominio condominio=new Condominio();condominio.setNome("Condomínio "+suffix);condominio.setEmpresa(empresa);
        condominio.setAtivo(true);condominio=condominioRepository.saveAndFlush(condominio);
        Produto produto=new Produto();produto.setEmpresa(empresa);produto.setCodigoInterno("SKU-"+suffix);
        produto.setNome("Produto "+suffix);produto.setPreco(new BigDecimal("10.990000"));produto.setCategoria(ProdutoCategoria.OUTROS);
        produto.setUnidadeMedida(UnidadeMedida.UN);produto.setStatus(true);produto=produtoRepository.saveAndFlush(produto);
        return new Fixture(empresa,condominio,produto);
    }
    private record Fixture(Empresa empresa,Condominio condominio,Produto produto){}
}
