package com.jefiro.app247.infra.controller;


import com.jefiro.app247.domain.model.dto.*;
import com.jefiro.app247.domain.model.dto.auth.ChangePasswordRequest;
import com.jefiro.app247.infra.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("user")
public class UserController {
    @Autowired
    UserService service;

    @PostMapping("/recuperar")
    public ResponseEntity<Map<String, String>> recuperar(@RequestBody @Valid PasswordRecovery request) {
        service.recoveryPassword(request.cpf());

        return ResponseEntity.accepted().body(
                Map.of("message", "Se o CPF existir, enviamos instruções para recuperação.")
        );
    }

    @PostMapping("/validar")
    public ResponseEntity<?> validar(@RequestBody @Valid ValidateCodeRequest passwordRecovery) {
        return ResponseEntity.ok(Map.of("token", service.verificarCode(passwordRecovery)));
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<?> redefinirSenha(@RequestBody @Valid ResetPasswordRequest request) {
        return ResponseEntity.ok(service.novaSenha(request));
    }

    @PostMapping("/alterar-senha")
    public ResponseEntity<Map<String, String>> alterarSenha(
            @RequestBody ChangePasswordRequest request
    ) {
        service.alterarSenha(request);
        return ResponseEntity.ok(Map.of("message", "Senha alterada com sucesso"));
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

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@RequestBody UserUpdate update, @PathVariable Long id) {
        service.atualizarUsuario(id, update);
        return ResponseEntity.ok(Map.of("message", "Usuário atualizado com sucesso"));
    }
}
