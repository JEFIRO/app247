package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.PagamentoResponse;
import com.jefiro.app247.infra.service.PagamentoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController()
@RequestMapping("/pagamento")
public class PagamentoController {
    @Autowired
    PagamentoService service;

    @GetMapping("/terminal")
    public PagamentoResponse getPagamento(@RequestParam String carrinho_id) {
        return service.gerarPix(carrinho_id);
    }

    @GetMapping("/checkout")
    public Map<?, ?> gerarCheckout(@RequestParam String carrinho_id, @RequestParam Long user_id) throws Exception {
        return service.gerarCheckout(carrinho_id, user_id);
    }


}
