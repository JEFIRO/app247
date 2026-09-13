package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.estoque.TransferenciaEstoqueRequest;
import com.jefiro.app247.domain.model.enum_type.StatusTransferenciaEstoque;
import com.jefiro.app247.infra.service.TransferenciaEstoqueService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/transferencias-estoque")
public class TransferenciaEstoqueController {
    @Autowired private TransferenciaEstoqueService service;
    @PostMapping public ResponseEntity<?> criar(@RequestBody @Valid TransferenciaEstoqueRequest r){return ResponseEntity.ok(service.criar(r));}
    @GetMapping public ResponseEntity<?> listar(@RequestParam(required=false)StatusTransferenciaEstoque status,Pageable pageable){return ResponseEntity.ok(service.listar(status,pageable));}
    @GetMapping("/{id}") public ResponseEntity<?> buscar(@PathVariable String id){return ResponseEntity.ok(service.buscar(id));}
    @PostMapping("/{id}/confirmar") public ResponseEntity<?> confirmar(@PathVariable String id){return ResponseEntity.ok(service.confirmar(id));}
    @PostMapping("/{id}/cancelar") public ResponseEntity<?> cancelar(@PathVariable String id){return ResponseEntity.ok(service.cancelar(id));}
}
