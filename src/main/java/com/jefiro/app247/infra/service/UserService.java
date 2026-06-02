package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.*;
import com.jefiro.app247.domain.model.dto.auth.AuthDTO;
import com.jefiro.app247.domain.model.dto.auth.AuthResponse;
import com.jefiro.app247.infra.event.UserCreatedEvent;
import com.jefiro.app247.infra.repository.UserRepository;
import com.mercadopago.net.HttpStatus;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
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
    @Autowired
    FileStorageService fileStorageService;


    public UserResponseDTO saveUser(UserRequestDTO request) {
        User user = new User(request);

        return new UserResponseDTO(repository.save(user));
    }

    public User getUser(Long user) {
        return repository.findById(user).orElseThrow(() -> new RuntimeException("Usuario não encontrado"));
    }

    public User getUser(String user) {
        return repository.getByCpf(user).orElseThrow(() -> new RuntimeException("Usuario não encontrado"));
    }


    public AuthResponse login(AuthDTO auth) {

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(auth.cpf(), auth.senha());

        Authentication authentication = authenticationManager.authenticate(authenticationToken);

        User user = (User) authentication.getPrincipal();

        String token = tokenService.generateToken(user);

        return new AuthResponse(
                token,
                new UserResponseDTO(user)
        );
    }

    public AuthResponse loginAdmin(AuthDTO auth) {

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(auth.cpf(), auth.senha());

        Authentication authentication = authenticationManager.authenticate(authenticationToken);
        User user = (User) authentication.getPrincipal();

        boolean autorizado =
                user.getRole() == RoleUser.ADMIN ||
                        user.getRole() == RoleUser.GERENTE;

        if (!autorizado) {
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(403),
                    "Você não possui permissão para esta ação"
            );
        }

        String token = tokenService.generateToken(user);

        return new AuthResponse(
                token,
                new UserResponseDTO(user)
        );
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

    public User cadastrar(@Valid UserRequestDTO requestDTO, RoleUser roleUser) {
        try {
            if (repository.existsByCpf(requestDTO.cpf())) {
                throw new IllegalArgumentException("já existe um usuario com esse cpf");
            }
            User user = new User(requestDTO);

            user.setRole(roleUser);

            user.setSenha(
                    passwordEncoder.encode(requestDTO.senha())
            );
            user = repository.save(user);

            publisher.publishEvent(
                    new UserCreatedEvent(user)
            );
            return user;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public boolean salvarFoto(MultipartFile file, Long id) {
        try {
            User user = repository.findById(id).orElseThrow(() -> new RuntimeException(""));

            user.setFotoPerfil(fileStorageService.salvarArquivo(file));

            repository.save(user);

            return true;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
