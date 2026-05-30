package com.jefiro.app247.infra.controller;


import com.jefiro.app247.domain.model.dto.PasswordRecovery;
import com.jefiro.app247.domain.model.dto.ResetPasswordRequest;
import com.jefiro.app247.infra.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

}
