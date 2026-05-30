package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.UserRequestDTO;
import com.jefiro.app247.domain.model.dto.auth.AuthDTO;
import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.infra.service.TokenService;
import com.jefiro.app247.infra.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("auth")
public class AuthController {
    @Autowired
    UserService service;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid AuthDTO auth) {
        return ResponseEntity.ok(service.login(auth));
    }

    @PostMapping("/register")
    public ResponseEntity<?> create(@RequestBody @Valid UserRequestDTO requestDTO) {
        service.cadastrar(requestDTO);

        return ResponseEntity.ok().build();
    }
}
