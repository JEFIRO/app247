package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.*;
import com.jefiro.app247.domain.model.dto.auth.AuthResponse;
import com.jefiro.app247.infra.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
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

    public UserResponseDTO saveUser(UserRequestDTO request) {
        User user = new User(request);

        return new UserResponseDTO(repository.save(user));
    }

    public User getUser(Long user) {
        return repository.findById(user).orElseThrow(() -> new RuntimeException("Usuario não encontrado"));
    }


    public AuthResponse login(LoginRequestDTO request) {
        return new AuthResponse(UUID.randomUUID().toString(), new UserResponseDTO(repository.findByEmail(request.email()).get()));
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


}
