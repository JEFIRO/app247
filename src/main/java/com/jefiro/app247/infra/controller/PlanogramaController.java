package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.estoque.*;
import com.jefiro.app247.infra.service.PlanogramaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/planogramas")
public class PlanogramaController {
    @Autowired private PlanogramaService service;
    @GetMapping public ResponseEntity<?> listar(){return ResponseEntity.ok(service.listar());}
    @GetMapping("/{id}") public ResponseEntity<?> detalhe(@PathVariable String id){return ResponseEntity.ok(service.detalhe(id));}
    @PostMapping public ResponseEntity<?> criar(@RequestBody @Valid PlanogramaRequest r){return ResponseEntity.ok(service.criar(r));}
    @PutMapping("/{id}") public ResponseEntity<?> alterar(@PathVariable String id,@RequestBody @Valid PlanogramaRequest r){return ResponseEntity.ok(service.alterar(id,r));}
    @PostMapping("/{id}/posicoes") public ResponseEntity<?> adicionarPosicao(@PathVariable String id,@RequestBody @Valid PlanogramaPosicaoRequest r){return ResponseEntity.ok(service.adicionarPosicao(id,r));}
    @PutMapping("/posicoes/{id}") public ResponseEntity<?> alterarPosicao(@PathVariable String id,@RequestBody @Valid PlanogramaPosicaoRequest r){return ResponseEntity.ok(service.alterarPosicao(id,r));}
    @PostMapping("/posicoes/{id}/produtos") public ResponseEntity<?> adicionarProduto(@PathVariable String id,@RequestBody @Valid PlanogramaProdutoRequest r){return ResponseEntity.ok(service.adicionarProduto(id,r));}
    @PutMapping("/produtos/{id}/posicao/{posicaoId}") public ResponseEntity<?> moverProduto(@PathVariable String id,@PathVariable String posicaoId){return ResponseEntity.ok(service.moverProduto(id,posicaoId));}
    @DeleteMapping("/produtos/{id}") public ResponseEntity<?> removerProduto(@PathVariable String id){return ResponseEntity.ok(service.removerProduto(id));}
}
