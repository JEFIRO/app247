package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.dto.estoque.*;
import com.jefiro.app247.infra.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PlanogramaService {
    @Autowired private PlanogramaRepository planogramaRepository;
    @Autowired private PlanogramaPosicaoRepository posicaoRepository;
    @Autowired private PlanogramaProdutoRepository produtoPlanogramaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private AuditLogService auditLogService;

    @Transactional
    public PlanogramaResponse criar(PlanogramaRequest request){
        String empresaId=EmpresaContext.require();String nome=request.nome().trim();
        if(planogramaRepository.existsByEmpresaIdAndNomeIgnoreCase(empresaId,nome))
            throw new IllegalStateException("Já existe um planograma com esse nome");
        Planograma planograma=new Planograma();planograma.setEmpresa(empresaRepository.getReferenceById(empresaId));
        aplicar(planograma,request);planograma=planogramaRepository.saveAndFlush(planograma);
        auditLogService.record(planograma.getEmpresa(),"PLANOGRAMA_CRIADO","Planograma",planograma.getId(),null,
                Map.of("nome",planograma.getNome()),Map.of());
        return resposta(planograma);
    }

    @Transactional
    public PlanogramaResponse alterar(String id,PlanogramaRequest request){
        Planograma planograma=buscar(id);String nome=request.nome().trim();
        if(planogramaRepository.existsByEmpresaIdAndNomeIgnoreCaseAndIdNot(planograma.getEmpresa().getId(),nome,id))
            throw new IllegalStateException("Já existe um planograma com esse nome");
        Map<String,Object> antes=Map.of("nome",planograma.getNome(),"ativo",planograma.getAtivo());
        aplicar(planograma,request);planogramaRepository.saveAndFlush(planograma);
        auditLogService.record(planograma.getEmpresa(),"PLANOGRAMA_ALTERADO","Planograma",id,antes,
                Map.of("nome",planograma.getNome(),"ativo",planograma.getAtivo()),Map.of());
        return resposta(planograma);
    }

    @Transactional(readOnly=true)
    public List<PlanogramaResponse> listar(){
        return planogramaRepository.findAllByEmpresaIdOrderByAtivoDescNomeAsc(EmpresaContext.require())
                .stream().map(this::resposta).toList();
    }

    @Transactional(readOnly=true)
    public PlanogramaResponse detalhe(String id){return resposta(buscar(id));}

    @Transactional
    public PlanogramaResponse adicionarPosicao(String planogramaId,PlanogramaPosicaoRequest request){
        Planograma planograma=buscar(planogramaId);PlanogramaPosicao posicao=new PlanogramaPosicao();
        posicao.setPlanograma(planograma);posicao.setEmpresa(planograma.getEmpresa());aplicar(posicao,request);
        posicaoRepository.save(posicao);registrarAlteracao(planograma,"posição adicionada");return resposta(planograma);
    }

    @Transactional
    public PlanogramaResponse alterarPosicao(String posicaoId,PlanogramaPosicaoRequest request){
        PlanogramaPosicao posicao=buscarPosicao(posicaoId);aplicar(posicao,request);posicaoRepository.save(posicao);
        registrarAlteracao(posicao.getPlanograma(),"posição alterada");return resposta(posicao.getPlanograma());
    }

    @Transactional
    public PlanogramaResponse adicionarProduto(String posicaoId,PlanogramaProdutoRequest request){
        String empresaId=EmpresaContext.require();PlanogramaPosicao posicao=buscarPosicao(posicaoId);
        Produto produto=produtoRepository.findByIdProdutoAndEmpresaId(request.produtoId(),empresaId)
                .orElseThrow(()->new IllegalArgumentException("Produto não pertence à empresa atual"));
        PlanogramaProduto vinculo=produtoPlanogramaRepository.findByPosicaoIdAndProdutoIdProduto(posicaoId,request.produtoId())
                .orElseGet(PlanogramaProduto::new);
        vinculo.setEmpresa(posicao.getEmpresa());vinculo.setPosicao(posicao);vinculo.setProduto(produto);aplicar(vinculo,request);
        produtoPlanogramaRepository.save(vinculo);registrarAlteracao(posicao.getPlanograma(),"produto posicionado");
        return resposta(posicao.getPlanograma());
    }

    @Transactional
    public PlanogramaResponse moverProduto(String vinculoId,String novaPosicaoId){
        String empresaId=EmpresaContext.require();
        PlanogramaProduto vinculo=produtoPlanogramaRepository.findByIdAndEmpresaId(vinculoId,empresaId)
                .orElseThrow(()->new NoSuchElementException("Posicionamento não encontrado"));
        PlanogramaPosicao nova=buscarPosicao(novaPosicaoId);
        if(!vinculo.getPosicao().getPlanograma().getId().equals(nova.getPlanograma().getId()))
            throw new IllegalArgumentException("A nova posição deve pertencer ao mesmo planograma");
        produtoPlanogramaRepository.findByPosicaoIdAndProdutoIdProduto(novaPosicaoId,vinculo.getProduto().getIdProduto())
                .filter(outro->!outro.getId().equals(vinculoId)).ifPresent(outro->{throw new IllegalStateException("Produto já ocupa esta posição");});
        vinculo.setPosicao(nova);produtoPlanogramaRepository.save(vinculo);registrarAlteracao(nova.getPlanograma(),"produto movido");
        return resposta(nova.getPlanograma());
    }

    @Transactional
    public PlanogramaResponse removerProduto(String vinculoId){
        String empresaId=EmpresaContext.require();PlanogramaProduto vinculo=produtoPlanogramaRepository.findByIdAndEmpresaId(vinculoId,empresaId)
                .orElseThrow(()->new NoSuchElementException("Posicionamento não encontrado"));
        vinculo.setAtivo(false);produtoPlanogramaRepository.save(vinculo);registrarAlteracao(vinculo.getPosicao().getPlanograma(),"produto removido");
        return resposta(vinculo.getPosicao().getPlanograma());
    }

    private Planograma buscar(String id){return planogramaRepository.findByIdAndEmpresaId(id,EmpresaContext.require())
            .orElseThrow(()->new NoSuchElementException("Planograma não encontrado"));}
    private PlanogramaPosicao buscarPosicao(String id){return posicaoRepository.findByIdAndEmpresaId(id,EmpresaContext.require())
            .orElseThrow(()->new NoSuchElementException("Posição não encontrada"));}
    private void aplicar(Planograma p,PlanogramaRequest r){p.setNome(r.nome().trim());p.setDescricao(r.descricao());p.setAtivo(r.ativo()==null||r.ativo());}
    private void aplicar(PlanogramaPosicao p,PlanogramaPosicaoRequest r){p.setSetor(r.setor());p.setCorredor(r.corredor());p.setEstante(r.estante());p.setModulo(r.modulo());p.setPrateleira(r.prateleira());p.setPosicao(r.posicao());p.setOrdem(r.ordem()==null?0:r.ordem());p.setX(r.x());p.setY(r.y());p.setLargura(r.largura());p.setAltura(r.altura());}
    private void aplicar(PlanogramaProduto p,PlanogramaProdutoRequest r){p.setFacings(r.facings()==null?1:r.facings());p.setCapacidade(r.capacidade());p.setQuantidadeIdeal(r.quantidadeIdeal());p.setQuantidadeMinima(r.quantidadeMinima());p.setAtivo(r.ativo()==null||r.ativo());}
    private void registrarAlteracao(Planograma p,String detalhe){auditLogService.record(p.getEmpresa(),"PLANOGRAMA_ALTERADO","Planograma",p.getId(),null,null,Map.of("alteracao",detalhe));}
    private PlanogramaResponse resposta(Planograma p){
        List<PlanogramaPosicao> posicoes=posicaoRepository.findAllByPlanogramaIdAndEmpresaIdOrderByOrdemAscIdAsc(p.getId(),p.getEmpresa().getId());
        Map<String,List<PlanogramaProduto>> produtos=produtoPlanogramaRepository
                .findAllByPosicaoPlanogramaIdAndEmpresaIdOrderByPosicaoOrdemAscCreatedAtAsc(p.getId(),p.getEmpresa().getId())
                .stream().collect(Collectors.groupingBy(v->v.getPosicao().getId()));
        return PlanogramaResponse.from(p,posicoes,produtos);
    }
}
