package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Item;
import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.dto.CarrinhoRequest;
import com.jefiro.app247.domain.model.dto.ItemRequest;
import com.jefiro.app247.infra.repository.CarrinhoRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class CarrinhoService {

    @Autowired
    private CarrinhoRepository repository;

    @Autowired
    private ProdutoService produtoService;
    @Autowired
    private TerminalRepository terminalRepository;

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
            Item item = new Item(produto, i.quantity(), i.receivedWeight());
            item.setEmpresa(produto.getEmpresa());
            carrinho.addItem(item);
            sub = sub.add(
                    produto.getPreco().multiply(BigDecimal.valueOf(item.getQuantity()))
            );
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

}
