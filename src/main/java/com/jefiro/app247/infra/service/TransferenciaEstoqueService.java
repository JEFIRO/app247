package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.estoque.*;
import com.jefiro.app247.domain.model.enum_type.*;
import com.jefiro.app247.infra.event.ProdutoCatalogChangedEvent;
import com.jefiro.app247.infra.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class TransferenciaEstoqueService {
    @Autowired private TransferenciaEstoqueRepository transferenciaRepository;
    @Autowired private EstoqueEmpresaRepository estoqueEmpresaRepository;
    @Autowired private EstoqueCondominioRepository estoqueCondominioRepository;
    @Autowired private MovimentacaoEstoqueRepository movimentoRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private CondominioRepository condominioRepository;
    @Autowired private CondominioService condominioService;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private AuditLogService auditLogService;

    @Transactional
    public TransferenciaEstoqueResponse criar(TransferenciaEstoqueRequest request){
        String empresaId=EmpresaContext.require();
        Condominio destino=condominioService.buscarDoTenant(request.condominioDestinoId(),empresaId);
        Empresa empresa=empresaRepository.getReferenceById(empresaId);
        Set<String> produtosUnicos=new HashSet<>();
        TransferenciaEstoque transferencia=new TransferenciaEstoque();
        transferencia.setEmpresa(empresa);transferencia.setOrigemTipo(TipoLocalEstoque.ESTOQUE_EMPRESA);
        transferencia.setOrigemId(empresaId);transferencia.setDestinoTipo(TipoLocalEstoque.CONDOMINIO);
        transferencia.setDestinoId(destino.getIdCondominio());transferencia.setObservacao(request.observacao());
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication!=null&&authentication.getPrincipal() instanceof User user
                && user.getEmpresa()!=null&&empresaId.equals(user.getEmpresa().getId()))transferencia.setCreatedBy(user);
        for(TransferenciaEstoqueRequest.Item requestItem:request.itens()){
            if(!produtosUnicos.add(requestItem.produtoId()))throw new IllegalArgumentException("Produto duplicado na transferência");
            Produto produto=produtoRepository.findByIdProdutoAndEmpresaId(requestItem.produtoId(),empresaId)
                    .orElseThrow(()->new IllegalArgumentException("Produto não pertence à empresa atual"));
            TransferenciaEstoqueItem item=new TransferenciaEstoqueItem();item.setTransferencia(transferencia);
            item.setEmpresa(empresa);item.setProduto(produto);item.setQuantidade(quantidadePositiva(requestItem.quantidade()));
            transferencia.getItens().add(item);
        }
        transferencia=transferenciaRepository.saveAndFlush(transferencia);
        auditLogService.record(empresa,"TRANSFERENCIA_CRIADA","TransferenciaEstoque",transferencia.getId(),null,
                Map.of("destinoCondominioId",destino.getIdCondominio(),"itens",transferencia.getItens().size()),Map.of());
        return resposta(transferencia,false);
    }

    @Transactional
    public TransferenciaEstoqueResponse confirmar(String id){
        String empresaId=EmpresaContext.require();
        TransferenciaEstoque transferencia=transferenciaRepository.findForUpdate(id,empresaId)
                .orElseThrow(()->new NoSuchElementException("Transferência não encontrada"));
        if(transferencia.getStatus()==StatusTransferenciaEstoque.CONCLUIDA)return resposta(transferencia,true);
        if(transferencia.getStatus()==StatusTransferenciaEstoque.CANCELADA)
            throw new IllegalStateException("Transferência cancelada não pode ser confirmada");
        if(transferencia.getOrigemTipo()!=TipoLocalEstoque.ESTOQUE_EMPRESA
                ||transferencia.getDestinoTipo()!=TipoLocalEstoque.CONDOMINIO)
            throw new IllegalStateException("Direção de transferência ainda não suportada");
        Condominio destino=condominioService.buscarDoTenant(transferencia.getDestinoId(),empresaId);

        List<TransferenciaEstoqueItem> itens=transferencia.getItens().stream()
                .sorted(Comparator.comparing(i->i.getProduto().getIdProduto())).toList();
        for(TransferenciaEstoqueItem item:itens){
            Produto produto=item.getProduto();
            if(!empresaId.equals(produto.getEmpresa().getId()))throw new IllegalStateException("Produto cross-tenant na transferência");
            EstoqueEmpresa origem=estoqueEmpresaRepository.findForUpdate(empresaId,produto.getIdProduto())
                    .orElseGet(()->criarEstoqueEmpresa(transferencia.getEmpresa(),produto));
            EstoqueCondominio estoqueDestino=estoqueCondominioRepository.findForUpdate(destino.getIdCondominio(),produto.getIdProduto())
                    .orElseGet(()->criarEstoqueCondominio(transferencia.getEmpresa(),destino,produto));
            BigDecimal quantidade=quantidadePositiva(item.getQuantidade());
            movimentarOrigem(origem,quantidade.negate(),transferencia,item);
            movimentarDestino(estoqueDestino,quantidade,transferencia,item);
            eventPublisher.publishEvent(new ProdutoCatalogChangedEvent(produto.getIdProduto(),
                    ProdutoCatalogChangeReason.STOCK_TRANSFER_COMPLETED,Set.of(destino.getIdCondominio())));
        }
        transferencia.setStatus(StatusTransferenciaEstoque.CONCLUIDA);transferencia.setConfirmedAt(Instant.now());
        transferenciaRepository.saveAndFlush(transferencia);
        auditLogService.record(transferencia.getEmpresa(),"TRANSFERENCIA_CONFIRMADA","TransferenciaEstoque",id,
                Map.of("status","RASCUNHO"),Map.of("status","CONCLUIDA"),Map.of("itens",itens.size()));
        return resposta(transferencia,true);
    }

    @Transactional
    public TransferenciaEstoqueResponse cancelar(String id){
        TransferenciaEstoque transferencia=transferenciaRepository.findForUpdate(id,EmpresaContext.require())
                .orElseThrow(()->new NoSuchElementException("Transferência não encontrada"));
        if(transferencia.getStatus()==StatusTransferenciaEstoque.CANCELADA)return resposta(transferencia,true);
        if(transferencia.getStatus()==StatusTransferenciaEstoque.CONCLUIDA)
            throw new IllegalStateException("Transferência concluída exige uma operação futura de estorno; o histórico não será reescrito");
        transferencia.setStatus(StatusTransferenciaEstoque.CANCELADA);transferencia.setCancelledAt(Instant.now());
        transferenciaRepository.saveAndFlush(transferencia);
        auditLogService.record(transferencia.getEmpresa(),"TRANSFERENCIA_CANCELADA","TransferenciaEstoque",id,
                Map.of("status","RASCUNHO"),Map.of("status","CANCELADA"),Map.of());
        return resposta(transferencia,true);
    }

    @Transactional(readOnly=true)
    public TransferenciaEstoqueResponse buscar(String id){
        TransferenciaEstoque transferencia=transferenciaRepository.findByIdAndEmpresaId(id,EmpresaContext.require())
                .orElseThrow(()->new NoSuchElementException("Transferência não encontrada"));
        return resposta(transferencia,true);
    }

    @Transactional(readOnly=true)
    public Page<TransferenciaEstoqueResponse> listar(StatusTransferenciaEstoque status,Pageable pageable){
        String empresaId=EmpresaContext.require();
        Page<TransferenciaEstoque> pagina=status==null?transferenciaRepository.findAllByEmpresaId(empresaId,pageable)
                :transferenciaRepository.findAllByEmpresaIdAndStatus(empresaId,status,pageable);
        return pagina.map(t->resposta(t,false));
    }

    private EstoqueEmpresa criarEstoqueEmpresa(Empresa empresa,Produto produto){
        EstoqueEmpresa estoque=new EstoqueEmpresa();estoque.setEmpresa(empresa);estoque.setProduto(produto);
        estoque.setQuantidade(BigDecimal.ZERO.setScale(3));estoque.setAtivo(true);
        return estoqueEmpresaRepository.saveAndFlush(estoque);
    }
    private EstoqueCondominio criarEstoqueCondominio(Empresa empresa,Condominio condominio,Produto produto){
        EstoqueCondominio estoque=new EstoqueCondominio();estoque.setEmpresa(empresa);estoque.setCondominio(condominio);
        estoque.setProduto(produto);estoque.setQuantidade(BigDecimal.ZERO.setScale(3));estoque.setAtivo(true);
        return estoqueCondominioRepository.saveAndFlush(estoque);
    }
    private void movimentarOrigem(EstoqueEmpresa estoque,BigDecimal delta,TransferenciaEstoque transferencia,TransferenciaEstoqueItem item){
        BigDecimal anterior=estoque.getQuantidade();BigDecimal posterior=anterior.add(delta);
        estoque.setQuantidade(posterior);estoqueEmpresaRepository.save(estoque);
        MovimentacaoEstoque movimento=movimentoBase(transferencia,item,delta,anterior,posterior,TipoMovimentacaoEstoque.SAIDA_TRANSFERENCIA,"SOURCE");
        movimento.setEstoqueEmpresa(estoque);movimentoRepository.save(movimento);
    }
    private void movimentarDestino(EstoqueCondominio estoque,BigDecimal delta,TransferenciaEstoque transferencia,TransferenciaEstoqueItem item){
        BigDecimal anterior=estoque.getQuantidade();BigDecimal posterior=anterior.add(delta);
        estoque.setQuantidade(posterior);estoque.setAtivo(true);estoqueCondominioRepository.save(estoque);
        MovimentacaoEstoque movimento=movimentoBase(transferencia,item,delta,anterior,posterior,TipoMovimentacaoEstoque.ENTRADA_TRANSFERENCIA,"DESTINATION");
        movimento.setEstoque(estoque);movimentoRepository.save(movimento);
    }
    private MovimentacaoEstoque movimentoBase(TransferenciaEstoque transferencia,TransferenciaEstoqueItem item,
                                                BigDecimal delta,BigDecimal anterior,BigDecimal posterior,
                                                TipoMovimentacaoEstoque tipo,String lado){
        MovimentacaoEstoque movimento=new MovimentacaoEstoque();movimento.setEmpresa(transferencia.getEmpresa());
        movimento.setTransferencia(transferencia);movimento.setTipo(tipo);movimento.setQuantidade(delta);
        movimento.setCreatedBy(transferencia.getCreatedBy());
        movimento.setQuantidadeAnterior(anterior);movimento.setQuantidadePosterior(posterior);
        movimento.setMotivo("Transferência "+transferencia.getId());
        movimento.setChaveIdempotencia("TRANSFER:"+transferencia.getId()+":"+item.getId()+":"+lado);
        return movimento;
    }
    private BigDecimal quantidadePositiva(BigDecimal valor){BigDecimal quantidade=EstoqueEmpresaService.quantidade(valor);
        if(quantidade.signum()<=0)throw new IllegalArgumentException("Quantidade da transferência deve ser maior que zero");return quantidade;}
    private TransferenciaEstoqueResponse resposta(TransferenciaEstoque transferencia,boolean incluirMovimentos){
        List<MovimentacaoEstoqueResponse> movimentos=incluirMovimentos
                ?movimentoRepository.findAllByTransferenciaIdOrderByCreatedAtAsc(transferencia.getId()).stream().map(MovimentacaoEstoqueResponse::new).toList()
                :List.of();
        String destinoNome=transferencia.getDestinoTipo()==TipoLocalEstoque.CONDOMINIO
                ?condominioRepository.findByIdCondominioAndEmpresaId(
                        transferencia.getDestinoId(),transferencia.getEmpresa().getId())
                        .map(Condominio::getNome).orElse(transferencia.getDestinoId())
                :transferencia.getDestinoId();
        return TransferenciaEstoqueResponse.from(transferencia,destinoNome,movimentos);
    }
}
