package com.jefiro.app247.infra.controller;


import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.dto.CreateProductDTO;
import com.jefiro.app247.domain.model.dto.OrderDTO;
import com.jefiro.app247.domain.model.dto.PasswordRecovery;
import com.jefiro.app247.domain.model.dto.ResetPasswordRequest;
import com.jefiro.app247.infra.service.FileStorageService;
import com.jefiro.app247.infra.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("user")
public class UserController {
    @Autowired
    UserService service;


    @PostMapping("/reculperar")
    public ResponseEntity<?> reculperar(@RequestBody PasswordRecovery passwordRecovery) {
        return ResponseEntity.ok(service.recoveryPassword(passwordRecovery.cpf()));
    }

    @PostMapping("/validar")
    public ResponseEntity<?> validar(@RequestBody PasswordRecovery passwordRecovery) {
        return ResponseEntity.ok(Map.of("token", service.verificarCode(passwordRecovery)));
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<?> redefinirSenha(@RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(service.novaSenha(request));
    }

    @GetMapping("/{userId}/orders")
    public ResponseEntity<Page<OrderDTO>> getOrdersByUser(
            @PathVariable Long userId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(service.getOrderByUser(userId, pageable));
    }

    @PostMapping(value = "foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> salvar(@RequestPart(value = "file") MultipartFile file, @RequestParam Long id) {
        return ResponseEntity.ok(service.salvarFoto(file, id));
    }
}
