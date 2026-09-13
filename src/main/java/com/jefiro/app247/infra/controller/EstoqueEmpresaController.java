package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.estoque.MovimentarEstoqueEmpresaRequest;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.infra.service.EstoqueEmpresaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/estoque-empresa")
public class EstoqueEmpresaController {
    @Autowired private EstoqueEmpresaService service;
    @GetMapping public ResponseEntity<?> listar(@RequestParam(required=false)String busca,
            @RequestParam(required=false)ProdutoCategoria categoria,@RequestParam(required=false)Boolean ativo,
            Pageable pageable){return ResponseEntity.ok(service.listar(busca,categoria,ativo,pageable));}
    @GetMapping("/produtos") public ResponseEntity<?> produtos(Pageable pageable){return ResponseEntity.ok(service.listarProdutos(pageable));}
    @GetMapping("/movimentacoes") public ResponseEntity<?> movimentos(){return ResponseEntity.ok(service.listarMovimentacoes());}
    @PostMapping("/{produtoId}/entrada") public ResponseEntity<?> entrada(@PathVariable String produtoId,@RequestBody @Valid MovimentarEstoqueEmpresaRequest r){return ResponseEntity.ok(service.entrada(produtoId,r.quantidade(),r.motivo()));}
    @PostMapping("/{produtoId}/saida") public ResponseEntity<?> saida(@PathVariable String produtoId,@RequestBody @Valid MovimentarEstoqueEmpresaRequest r){return ResponseEntity.ok(service.saida(produtoId,r.quantidade(),r.motivo()));}
    @PutMapping("/{produtoId}") public ResponseEntity<?> ajustar(@PathVariable String produtoId,@RequestBody @Valid MovimentarEstoqueEmpresaRequest r){return ResponseEntity.ok(service.ajustar(produtoId,r.quantidade(),r.motivo()));}
}
