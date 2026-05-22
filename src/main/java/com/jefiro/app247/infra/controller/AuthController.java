package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.UserRequestDTO;
import com.jefiro.app247.domain.model.dto.auth.AuthDTO;
import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.infra.service.TokenService;
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
    private AuthenticationManager authenticationManager;
    @Autowired
    private UserRepository repository;
    @Autowired
    TokenService tokenService;
    @Autowired
    PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid AuthDTO auth) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(auth.cpf(), auth.senha());

        Authentication authentication = authenticationManager.authenticate(authenticationToken);

        var token = tokenService.generateToken((User) authentication.getPrincipal());

        return ResponseEntity.ok(Map.of(
                "Token", token,
                "user", (User) authentication.getPrincipal()

        ));
    }

    @PostMapping("/register")
    public ResponseEntity<?> create(@RequestBody @Valid UserRequestDTO requestDTO) {
        if (repository.existsByCpf(requestDTO.cpf())) {
            throw new IllegalArgumentException("já existe um usuario com esse cpf");
        }
        User user = new User(requestDTO);

        user.setSenha(
                passwordEncoder.encode(requestDTO.senha())
        );
        repository.save(user);
        return ResponseEntity.ok().build();
    }

}
