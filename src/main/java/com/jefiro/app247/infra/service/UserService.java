package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.*;
import com.jefiro.app247.domain.model.dto.auth.AuthDTO;
import com.jefiro.app247.domain.model.dto.auth.AuthResponse;
import com.jefiro.app247.infra.event.UserCreatedEvent;
import com.jefiro.app247.infra.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {
    @Autowired
    RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private UserRepository repository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    AuthenticationManager authenticationManager;
    @Autowired
    TokenService tokenService;
    @Autowired
    private ApplicationEventPublisher publisher;


    public UserResponseDTO saveUser(UserRequestDTO request) {
        User user = new User(request);

        return new UserResponseDTO(repository.save(user));
    }

    public User getUser(Long user) {
        return repository.findById(user).orElseThrow(() -> new RuntimeException("Usuario não encontrado"));
    }


    public AuthResponse login(AuthDTO auth) {

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(auth.cpf(), auth.senha());

        Authentication authentication = authenticationManager.authenticate(authenticationToken);

        var token = tokenService.generateToken((User) authentication.getPrincipal());

        return new AuthResponse(token, (UserResponseDTO) authentication.getPrincipal());
    }

    public boolean recoveryPassword(String cpf) {
        try {
            System.out.println(cpf);
            User user = repository.getByCpf(cpf)
                    .orElseThrow(() -> new UsernameNotFoundException("Usuário não existe"));
            String code = String.valueOf(100000 + new Random().nextInt(900000));
            PasswordRecovery passwordRecovery = new PasswordRecovery(code, user.getEmail(), user.getCpf(), user.getNome());

            redisTemplate.opsForList().leftPush(
                    "recovery_queue",
                    passwordRecovery
            );

            redisTemplate.opsForValue().set(
                    "recovery:" + cpf,
                    passwordRecovery,
                    Duration.ofMinutes(15)
            );
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public String verificarCode(PasswordRecovery passwordRecovery) {
        try {
            PasswordRecovery recovery = (PasswordRecovery) redisTemplate
                    .opsForValue()
                    .get("recovery:" + passwordRecovery.cpf());

            if (recovery == null) {
                throw new RuntimeException("Código expirado");
            }

            if (!recovery.code().equals(passwordRecovery.code())) {
                throw new RuntimeException("Código inválido");
            }

            redisTemplate.delete(
                    "recovery:" + passwordRecovery.cpf()
            );


            String resetToken = UUID.randomUUID().toString();

            redisTemplate.opsForValue().set(
                    "reset:" + passwordRecovery.cpf(),
                    resetToken,
                    15,
                    TimeUnit.MINUTES
            );

            return resetToken;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean novaSenha(ResetPasswordRequest request) {
        try {
            String token = (String) redisTemplate.opsForValue()
                    .get("reset:" + request.cpf());

            if (token == null) {
                throw new RuntimeException("Token expirado");
            }

            if (!token.equals(request.token())) {
                throw new RuntimeException("Token inválido");
            }

            User user = repository.getByCpf(request.cpf())
                    .orElseThrow();

            user.setSenha(
                    passwordEncoder.encode(request.novaSenha())
            );

            repository.save(user);

            redisTemplate.delete("reset:" + request.cpf());
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public Page<OrderDTO> getOrderByUser(Long user_id, Pageable pageable) {
        return repository.findOrdersByUserId(user_id, pageable);
    }

    public void cadastrar(@Valid UserRequestDTO requestDTO) {
        try {
            if (repository.existsByCpf(requestDTO.cpf())) {
                throw new IllegalArgumentException("já existe um usuario com esse cpf");
            }
            User user = new User(requestDTO);

            user.setSenha(
                    passwordEncoder.encode(requestDTO.senha())
            );
            user = repository.save(user);


            publisher.publishEvent(
                    new UserCreatedEvent(user)
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
