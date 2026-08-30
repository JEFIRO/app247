package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Item;
import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.dto.CarrinhoRequest;
import com.jefiro.app247.domain.model.dto.ItemRequest;
import com.jefiro.app247.infra.repository.CarrinhoRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import com.jefiro.app247.infra.exception.PriceChangedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class CarrinhoService {

    @Autowired
    private CarrinhoRepository repository;

    @Autowired
    private ProdutoService produtoService;
    @Autowired
    private TerminalRepository terminalRepository;
    @Autowired
    private PricingService pricingService;

    @Transactional
    public Carrinho save(CarrinhoRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Carrinho não informado");
        }
        if (request.terminalId() == null || request.terminalId().isBlank()) {
            throw new IllegalArgumentException("Terminal do carrinho não informado");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Carrinho deve possuir ao menos um item");
        }
        Carrinho carrinho = new Carrinho();
        BigDecimal sub = BigDecimal.ZERO;
        var terminal = terminalRepository.findById(request.terminalId())
                .orElseThrow(() -> new IllegalArgumentException("Terminal não existe"));
        carrinho.setTerminal(terminal);
        Empresa empresa = terminal.getCondominio().getEmpresa();
        if (EmpresaContext.get() != null && !EmpresaContext.get().equals(empresa.getId())) {
            throw new IllegalArgumentException("Terminal não pertence à empresa do contexto");
        }
        java.util.Set<String> produtosIncluidos = new java.util.HashSet<>();
        List<ItemPrecificado> itens = new ArrayList<>();
        List<PriceChangedException.ChangedItem> precosAtuais = new ArrayList<>();
        boolean houveAumento = false;
        for (ItemRequest i : request.items()) {
            if (i == null || i.productId() == null || i.productId().isBlank()) {
                throw new IllegalArgumentException("Item e produto devem ser informados");
            }
            if (i.quantity() == null || i.quantity() <= 0) {
                throw new IllegalArgumentException("Quantidade do item deve ser positiva");
            }
            if (!produtosIncluidos.add(i.productId())) {
                throw new IllegalArgumentException("Produto duplicado no carrinho");
            }
            Produto produto = produtoService.buscarPorIdDoTenant(i.productId(), empresa.getId());
            var preco = pricingService.calcular(
                    produto, terminal.getCondominio(), LocalDateTime.now(ZoneOffset.UTC));
            itens.add(new ItemPrecificado(i, preco));
            boolean aumentou = i.expectedUnitPrice() != null
                    && preco.precoCalculado().compareTo(i.expectedUnitPrice()) > 0;
            houveAumento |= aumentou;
            if (i.expectedUnitPrice() != null) {
                precosAtuais.add(new PriceChangedException.ChangedItem(
                        produto.getIdProduto(), produto.getNome(), i.expectedUnitPrice(),
                        preco.precoOriginal(), preco.precoCalculado(), preco.emPromocao(),
                        preco.promocao() != null ? preco.promocao().getIdPromocao() : null,
                        preco.promocao() != null ? preco.promocao().getNome() : null,
                        i.quantity(), aumentou));
            }
            sub = sub.add(preco.subtotal(i.quantity()));
        }
        sub = MoneyPolicy.persistence(sub);
        if (houveAumento) {
            throw new PriceChangedException(precosAtuais, sub, MoneyPolicy.chargedForPersistence(sub));
        }
        for (ItemPrecificado precificado : itens) {
            ItemRequest i = precificado.request();
            Item item = new Item(precificado.preco(), i.quantity(), i.receivedWeight());
            item.setEmpresa(precificado.preco().produto().getEmpresa());
            carrinho.addItem(item);
        }

        carrinho.setSubtotal(sub);
        carrinho.setEmpresa(empresa);
        carrinho = repository.save(carrinho);

        return carrinho;
    }

    public Carrinho save(Carrinho carrinho) {
        return repository.save(carrinho);
    }


    public Carrinho getById(String id) {
        if (EmpresaContext.get() != null) {
            return repository.findByIdCarrinhoAndEmpresaId(id, EmpresaContext.get())
                    .orElseThrow(() -> new IllegalArgumentException("Carrinho não existe para a empresa"));
        }
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Carrinho não existe"));
    }

    public Carrinho getByIdForUpdate(String id) {
        Carrinho carrinho = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Carrinho não existe"));
        if (EmpresaContext.get() != null
                && (carrinho.getEmpresa() == null
                || !EmpresaContext.get().equals(carrinho.getEmpresa().getId()))) {
            throw new IllegalArgumentException("Carrinho não existe para a empresa");
        }
        return carrinho;
    }

    public void validarParaPagamento(Carrinho carrinho) {
        if (carrinho == null || carrinho.getTerminal() == null
                || carrinho.getTerminal().getCondominio() == null
                || carrinho.getTerminal().getCondominio().getEmpresa() == null) {
            throw new IllegalStateException("Carrinho sem terminal, condomínio ou empresa válidos");
        }
        String empresaTerminal = carrinho.getTerminal().getCondominio().getEmpresa().getId();
        if (carrinho.getEmpresa() == null || !empresaTerminal.equals(carrinho.getEmpresa().getId())) {
            throw new IllegalStateException("Empresa do carrinho diverge da empresa do terminal");
        }
        if (carrinho.getItems() == null || carrinho.getItems().isEmpty()) {
            throw new IllegalStateException("Carrinho sem itens não pode ser cobrado");
        }

        BigDecimal subtotalCalculado = BigDecimal.ZERO;
        for (Item item : carrinho.getItems()) {
            if (item == null || item.getProduto() == null || item.getUnitPrice() == null
                    || item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalStateException("Carrinho possui item inconsistente");
            }
            if (item.getEmpresa() == null || item.getProduto().getEmpresa() == null
                    || !empresaTerminal.equals(item.getEmpresa().getId())
                    || !empresaTerminal.equals(item.getProduto().getEmpresa().getId())) {
                throw new IllegalStateException("Carrinho possui item de outra empresa");
            }
            subtotalCalculado = subtotalCalculado.add(
                    item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
            );
        }
        if (carrinho.getSubtotal() == null
                || carrinho.getSubtotal().compareTo(subtotalCalculado) != 0
                || subtotalCalculado.signum() <= 0) {
            throw new IllegalStateException("Subtotal do carrinho é inválido");
        }
    }

    @Transactional
    public void reprecificarParaCheckout(Carrinho carrinho) {
        List<PriceChangedException.ChangedItem> precosAtuais = new ArrayList<>();
        boolean houveAumento = false;
        BigDecimal total = BigDecimal.ZERO;
        var condominio = carrinho.getTerminal().getCondominio();
        LocalDateTime agora = LocalDateTime.now(ZoneOffset.UTC);
        List<PrecoAtualizacao> atualizacoes = new ArrayList<>();
        for (Item item : carrinho.getItems()) {
            var atual = pricingService.calcular(item.getProduto(), condominio, agora);
            atualizacoes.add(new PrecoAtualizacao(item, atual));
            total = total.add(atual.subtotal(item.getQuantity()));
            boolean aumentou = atual.precoCalculado().compareTo(item.getUnitPrice()) > 0;
            houveAumento |= aumentou;
            precosAtuais.add(new PriceChangedException.ChangedItem(
                        item.getProduto().getIdProduto(), item.getName(), item.getUnitPrice(),
                        atual.precoOriginal(), atual.precoCalculado(), atual.emPromocao(),
                        atual.promocao() != null ? atual.promocao().getIdPromocao() : null,
                        atual.promocao() != null ? atual.promocao().getNome() : null,
                        item.getQuantity(), aumentou));
        }
        total = MoneyPolicy.persistence(total);
        if (houveAumento) {
            throw new PriceChangedException(precosAtuais, total, MoneyPolicy.chargedForPersistence(total));
        }
        for (PrecoAtualizacao atualizacao : atualizacoes) {
            Item item = atualizacao.item();
            var preco = atualizacao.preco();
            item.setOriginalPrice(preco.precoOriginal());
            item.setUnitPrice(preco.precoCalculado());
            item.setCalculatedDiscount(preco.descontoCalculado());
            item.setCalculatedSubtotal(MoneyPolicy.persistence(preco.subtotal(item.getQuantity())));
            item.setPromocao(preco.promocao());
            item.setPromotionType(preco.promocao() != null ? preco.promocao().getTipo() : null);
            item.setPromotionValue(preco.promocao() != null ? preco.promocao().getValor() : null);
        }
        carrinho.setSubtotal(total);
        repository.saveAndFlush(carrinho);
    }

    private record ItemPrecificado(ItemRequest request,
                                   com.jefiro.app247.domain.model.dto.PrecoCalculado preco) {}
    private record PrecoAtualizacao(Item item,
                                    com.jefiro.app247.domain.model.dto.PrecoCalculado preco) {}

}
