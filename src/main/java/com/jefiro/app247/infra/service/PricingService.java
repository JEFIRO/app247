package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.Promocao;
import com.jefiro.app247.domain.model.dto.PrecoCalculado;
import com.jefiro.app247.domain.model.enum_type.AbrangenciaPromocao;
import com.jefiro.app247.infra.repository.EstoqueCondominioRepository;
import com.jefiro.app247.infra.repository.PromocaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;

@Service
public class PricingService {
    @Autowired private PromocaoRepository promocaoRepository;
    @Autowired private EstoqueCondominioRepository estoqueRepository;

    public PrecoCalculado calcular(Produto produto, Condominio condominio, LocalDateTime agoraUtc) {
        validarDisponibilidade(produto, condominio);
        BigDecimal original = MoneyPolicy.persistence(produto.getPreco());

        return promocaoRepository.findAplicaveis(
                        produto.getEmpresa().getId(), condominio.getIdCondominio(),
                        produto.getIdProduto(), agoraUtc).stream()
                .map(promocao -> candidato(promocao, original))
                .filter(candidato -> candidato.preco().compareTo(original) < 0)
                .min(COMPARATOR)
                .map(candidato -> new PrecoCalculado(
                        produto, original, candidato.preco(),
                        MoneyPolicy.persistence(original.subtract(candidato.preco())),
                        candidato.promocao()))
                .orElseGet(() -> new PrecoCalculado(
                        produto, original, original, MoneyPolicy.persistence(BigDecimal.ZERO), null));
    }

    private void validarDisponibilidade(Produto produto, Condominio condominio) {
        if (produto == null || condominio == null || produto.getEmpresa() == null
                || condominio.getEmpresa() == null
                || !produto.getEmpresa().getId().equals(condominio.getEmpresa().getId())) {
            throw new IllegalArgumentException("Produto e condomínio pertencem a empresas diferentes");
        }
        if (!produto.isStatus() || estoqueRepository
                .findByCondominioIdCondominioAndProdutoIdProdutoAndAtivoTrue(
                        condominio.getIdCondominio(), produto.getIdProduto()).isEmpty()) {
            throw new IllegalArgumentException("Produto não está disponível neste condomínio.");
        }
    }

    private Candidato candidato(Promocao promocao, BigDecimal original) {
        BigDecimal calculado = switch (promocao.getTipo()) {
            case PERCENTUAL -> original.multiply(MoneyPolicy.percentageFactor(promocao.getValor()));
            case DESCONTO_FIXO -> original.subtract(promocao.getValor());
            case PRECO_FIXO -> promocao.getValor();
        };
        if (calculado.signum() < 0) calculado = BigDecimal.ZERO;
        return new Candidato(promocao, MoneyPolicy.persistence(calculado));
    }

    private static final Comparator<Candidato> COMPARATOR = Comparator
            .comparing(Candidato::preco)
            .thenComparing(c -> c.promocao().getAbrangencia() == AbrangenciaPromocao.CONDOMINIO ? 0 : 1)
            .thenComparing((Candidato c) -> c.promocao().getPrioridade(), Comparator.reverseOrder())
            .thenComparing(c -> c.promocao().getIdPromocao());

    private record Candidato(Promocao promocao, BigDecimal preco) {
    }
}
