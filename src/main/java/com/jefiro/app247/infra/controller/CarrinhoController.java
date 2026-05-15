package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.dto.CarrinhoRequest;
import com.jefiro.app247.domain.model.dto.response.CarrinhoResponseDTO;
import com.jefiro.app247.infra.service.CarrinhoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("carrinho")
public class CarrinhoController {
    @Autowired
    private CarrinhoService service;

    @GetMapping("/{id}")
    public ResponseEntity<?> getCarrinho(@PathVariable String id) {
        return
                ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<?> addCarinho(@RequestBody @Valid CarrinhoRequest request) {
        return ResponseEntity.ok(new CarrinhoResponseDTO(service.save(request)));
    }

}
