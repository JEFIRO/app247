package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.dto.CheckoutSessionResponseDTO;
import com.jefiro.app247.infra.service.CarrinhoService;
import com.jefiro.app247.infra.service.CheckoutSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/checkout")
public class CheckoutSessionController {

    @Autowired
    private CheckoutSessionService service;
    @Autowired
    private CarrinhoService carrinhoService;

    @GetMapping(value = "/carrinho")
    public ResponseEntity<CheckoutSessionResponseDTO> getQrCode(@RequestParam String idCarrinho) {
        Carrinho carrinho = carrinhoService.getById(idCarrinho);
        
        return ResponseEntity.ok(new CheckoutSessionResponseDTO(service.create(carrinho)));
    }

    @GetMapping(value = "/session")
    public ResponseEntity<?> getCarrinho(@RequestParam String idSession) {
        return ResponseEntity.ok(service.getCarrinho(idSession));
    }

    @GetMapping(value = "/qrcode", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQRCode(@RequestParam String id) throws Exception {
        return ResponseEntity.ok(service.gerarQRCode(id));
    }

    @GetMapping()
    public ResponseEntity<?> setUser(@Valid @RequestParam @NotBlank String id, @Valid @RequestParam @NotBlank String session) {
        return ResponseEntity.ok(service.setUser(id, session));
    }

}
