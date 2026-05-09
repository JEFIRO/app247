package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Item;
import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.dto.CarrinhoRequest;
import com.jefiro.app247.domain.model.dto.ItemRequest;
import com.jefiro.app247.infra.repository.CarrinhoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CarrinhoService {

    @Autowired
    private CarrinhoRepository repository;

    @Autowired
    private ProdutoService produtoService;

    public Carrinho save(CarrinhoRequest request) {
        Carrinho carrinho = new Carrinho();
        List<Item> items = new ArrayList<>();
        BigDecimal sub = BigDecimal.ZERO;
        carrinho.setTerminalId(request.terminalId());

        for (ItemRequest i : request.items()) {
            Produto produto = produtoService.buscarPorId(i.productId());
            Item item = new Item(produto, i.quantity(), i.receivedWeight());
            item.setCarrinho(carrinho);
            items.add(item);
            sub = sub.add(
                    produto.getPreco()
                            .multiply(BigDecimal.valueOf(item.getQuantity()))
            );
        }

        carrinho.setSubtotal(sub);
        carrinho.setItems(items);

        carrinho = repository.save(carrinho);

        return carrinho;
    }


    public Carrinho getById(String id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Carrinho não existe"));
    }
}
