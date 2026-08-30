package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.estoque.*;
import com.jefiro.app247.infra.service.EstoqueService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class EstoqueController {
    @Autowired private EstoqueService service;

    @GetMapping("/condominios/{condominioId}/estoque")
    public ResponseEntity<?> listar(@PathVariable String condominioId) {
        return ResponseEntity.ok(service.listarCondominio(condominioId));
    }
    @PostMapping("/condominios/{condominioId}/estoque")
    public ResponseEntity<?> disponibilizar(@PathVariable String condominioId, @RequestBody @Valid EstoqueRequest request) {
        return ResponseEntity.ok(service.disponibilizar(
                condominioId, request.produtoId(), request.quantidade(), request.ativo()));
    }
    @PostMapping("/condominios/{condominioId}/estoque/{produtoId}/entrada")
    public ResponseEntity<?> entrada(@PathVariable String condominioId, @PathVariable String produtoId,
                                     @RequestBody @Valid QuantidadeEstoqueRequest request) {
        return ResponseEntity.ok(service.entrada(condominioId, produtoId, request.quantidade(), request.motivo()));
    }
    @PutMapping("/condominios/{condominioId}/estoque/{produtoId}")
    public ResponseEntity<?> ajustar(@PathVariable String condominioId, @PathVariable String produtoId,
                                     @RequestBody @Valid QuantidadeEstoqueRequest request) {
        return ResponseEntity.ok(service.ajustar(condominioId, produtoId, request.quantidade(), request.motivo()));
    }
    @DeleteMapping("/condominios/{condominioId}/estoque/{produtoId}")
    public ResponseEntity<?> remover(@PathVariable String condominioId, @PathVariable String produtoId) {
        return ResponseEntity.ok(service.remover(condominioId, produtoId));
    }
    @GetMapping("/estoque/geral")
    public ResponseEntity<?> geral() { return ResponseEntity.ok(service.listarGeral()); }

    @GetMapping("/terminais/{terminalId}/produtos-disponiveis")
    public ResponseEntity<?> produtosDoTerminal(@PathVariable String terminalId) {
        return ResponseEntity.ok(service.listarProdutosDoTerminal(terminalId));
    }

    @GetMapping("/condominios/{condominioId}/estoque/movimentacoes")
    public ResponseEntity<?> movimentacoes(@PathVariable String condominioId) {
        return ResponseEntity.ok(service.listarMovimentacoes(condominioId));
    }
}
