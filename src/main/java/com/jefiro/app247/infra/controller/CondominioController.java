package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.CadastroCompletoRequest;
import com.jefiro.app247.infra.service.CondominioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("condominio")
public class CondominioController {
    @Autowired
    CondominioService service;

    @PostMapping
    public ResponseEntity<?> save(@RequestBody @Valid CadastroCompletoRequest request) {
        return ResponseEntity.ok(service.newCondominio(request));
    }

    @GetMapping
    public ResponseEntity<?> get(Pageable pageable) {
        return ResponseEntity.ok(service.getCondominio(pageable));
    }

}
