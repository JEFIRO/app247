package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.dto.ProdutoResponse;
import com.jefiro.app247.domain.model.dto.estoque.EstoqueEmpresaResponse;
import com.jefiro.app247.domain.model.dto.estoque.MovimentacaoEstoqueResponse;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.TipoMovimentacaoEstoque;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EstoqueEmpresaService {
    @Autowired private EstoqueEmpresaRepository estoqueRepository;
    @Autowired private MovimentacaoEstoqueRepository movimentoRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private PlanogramaProdutoRepository planogramaProdutoRepository;
    @Autowired private AuditLogService auditLogService;

    @Transactional(readOnly=true)
    public Page<EstoqueEmpresaResponse> listar(String busca, ProdutoCategoria categoria,
                                               Boolean ativo, Pageable pageable) {
        String empresaId=EmpresaContext.require();
        String termo=busca==null||busca.isBlank()?null:busca.trim();
        Page<EstoqueEmpresa> pagina=estoqueRepository.pesquisar(empresaId,termo,categoria,ativo,pageable);
        List<String> produtos=pagina.getContent().stream().map(e->e.getProduto().getIdProduto()).toList();
        Map<String,PlanogramaProduto> localizacoes=new HashMap<>();
        if(!produtos.isEmpty()){
            planogramaProdutoRepository.findAllByEmpresaIdAndProdutoIdProdutoInAndAtivoTrue(empresaId,produtos)
                    .stream().sorted(java.util.Comparator.comparing(p->p.getPosicao().getOrdem()))
                    .forEach(p->localizacoes.putIfAbsent(p.getProduto().getIdProduto(),p));
        }
        return pagina.map(estoque->{
            PlanogramaProduto local=localizacoes.get(estoque.getProduto().getIdProduto());
            return new EstoqueEmpresaResponse(estoque,
                    local==null?null:com.jefiro.app247.domain.model.dto.estoque.PlanogramaResponse.localizacao(local.getPosicao()),
                    local==null?null:local.getCapacidade());
        });
    }

    @Transactional(readOnly=true)
    public Page<ProdutoResponse> listarProdutos(Pageable pageable) {
        return produtoRepository.findAllByEmpresaId(EmpresaContext.require(),pageable).map(ProdutoResponse::new);
    }

    @Transactional
    public EstoqueEmpresaResponse entrada(String produtoId,BigDecimal quantidade,String motivo){
        BigDecimal delta=quantidade(quantidade);
        if(delta.signum()<=0)throw new IllegalArgumentException("A quantidade de entrada deve ser maior que zero");
        EstoqueEmpresa estoque=obterOuCriar(produtoId);
        movimentar(estoque,delta,TipoMovimentacaoEstoque.ENTRADA,motivo);
        auditLogService.record(estoque.getEmpresa(),"ESTOQUE_EMPRESA_ENTRADA","EstoqueEmpresa",estoque.getId(),
                null,Map.of("quantidade",delta,"saldo",estoque.getQuantidade()),metadata(motivo));
        return new EstoqueEmpresaResponse(estoque,null,null);
    }

    @Transactional
    public EstoqueEmpresaResponse saida(String produtoId,BigDecimal quantidade,String motivo){
        BigDecimal valor=quantidade(quantidade);
        if(valor.signum()<=0)throw new IllegalArgumentException("A quantidade de saída deve ser maior que zero");
        EstoqueEmpresa estoque=obterOuCriar(produtoId);
        movimentar(estoque,valor.negate(),TipoMovimentacaoEstoque.SAIDA,motivo);
        auditLogService.record(estoque.getEmpresa(),"ESTOQUE_EMPRESA_SAIDA","EstoqueEmpresa",estoque.getId(),
                null,Map.of("quantidade",valor.negate(),"saldo",estoque.getQuantidade()),metadata(motivo));
        return new EstoqueEmpresaResponse(estoque,null,null);
    }

    @Transactional
    public EstoqueEmpresaResponse ajustar(String produtoId,BigDecimal novoSaldo,String motivo){
        BigDecimal saldo=quantidade(novoSaldo);
        EstoqueEmpresa estoque=obterOuCriar(produtoId);
        BigDecimal anterior=estoque.getQuantidade();
        movimentar(estoque,saldo.subtract(anterior),TipoMovimentacaoEstoque.AJUSTE,motivo);
        auditLogService.record(estoque.getEmpresa(),"ESTOQUE_EMPRESA_AJUSTE","EstoqueEmpresa",estoque.getId(),
                Map.of("saldo",anterior),Map.of("saldo",estoque.getQuantidade()),metadata(motivo));
        return new EstoqueEmpresaResponse(estoque,null,null);
    }

    @Transactional(readOnly=true)
    public List<MovimentacaoEstoqueResponse> listarMovimentacoes(){
        return movimentoRepository.findAllByEstoqueEmpresaEmpresaIdOrderByCreatedAtDesc(EmpresaContext.require())
                .stream().map(MovimentacaoEstoqueResponse::new).toList();
    }

    private EstoqueEmpresa obterOuCriar(String produtoId){
        String empresaId=EmpresaContext.require();
        var existente=estoqueRepository.findForUpdate(empresaId,produtoId);
        if(existente.isPresent())return existente.get();
        Produto produto=produtoRepository.findByIdProdutoAndEmpresaId(produtoId,empresaId)
                .orElseThrow(()->new IllegalArgumentException("Produto não pertence à empresa atual"));
        EstoqueEmpresa estoque=new EstoqueEmpresa();
        estoque.setEmpresa(empresaRepository.getReferenceById(empresaId));
        estoque.setProduto(produto);
        estoque.setQuantidade(BigDecimal.ZERO.setScale(3));
        estoque.setAtivo(true);
        return estoqueRepository.saveAndFlush(estoque);
    }

    private void movimentar(EstoqueEmpresa estoque,BigDecimal delta,TipoMovimentacaoEstoque tipo,String motivo){
        BigDecimal anterior=estoque.getQuantidade();
        BigDecimal posterior=anterior.add(delta).setScale(3,RoundingMode.UNNECESSARY);
        estoque.setQuantidade(posterior);
        estoqueRepository.save(estoque);
        MovimentacaoEstoque movimento=new MovimentacaoEstoque();
        movimento.setEmpresa(estoque.getEmpresa());movimento.setEstoqueEmpresa(estoque);movimento.setTipo(tipo);
        movimento.setQuantidade(delta);movimento.setQuantidadeAnterior(anterior);movimento.setQuantidadePosterior(posterior);
        movimento.setCreatedBy(usuarioAtual(estoque.getEmpresa().getId()));
        movimento.setMotivo(motivo);movimento.setChaveIdempotencia("CENTRAL:MANUAL:"+UUID.randomUUID());
        movimentoRepository.save(movimento);
    }

    static BigDecimal quantidade(BigDecimal valor){
        if(valor==null)throw new IllegalArgumentException("Quantidade obrigatória");
        try{return valor.setScale(3,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){
            throw new IllegalArgumentException("Quantidade aceita no máximo 3 casas decimais");
        }
    }

    private Map<String,String> metadata(String motivo){return Map.of("motivo",motivo==null?"":motivo);}

    private User usuarioAtual(String empresaId){
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication!=null&&authentication.getPrincipal() instanceof User user
                && user.getEmpresa()!=null&&empresaId.equals(user.getEmpresa().getId()))return user;
        return null;
    }
}
