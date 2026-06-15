package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.*;
import com.jefiro.app247.domain.model.dto.auth.AuthDTO;
import com.jefiro.app247.domain.model.dto.auth.AuthResponse;
import com.jefiro.app247.domain.model.dto.auth.ChangePasswordRequest;
import com.jefiro.app247.infra.event.UserCreatedEvent;
import com.jefiro.app247.infra.exception.*;
import com.jefiro.app247.infra.repository.CondominioRepository;
import com.jefiro.app247.infra.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.NoSuchElementException;
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
    @Autowired
    CondominioRepository condominioRepository;

    public User getUser(Long user) {
        return repository.findById(user).orElseThrow(UserNotFoundException::new);
    }

    private User findByCpf(String cpf) {
        return repository.getByCpf(cpf).orElseThrow(UserNotFoundException::new);
    }


    public AuthResponse login(AuthDTO auth) {

        try {
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(
                            auth.cpf(),
                            auth.senha()
                    );

            Authentication authentication =
                    authenticationManager.authenticate(authenticationToken);

            User user = (User) authentication.getPrincipal();

            String token = tokenService.generateToken(user);

            return new AuthResponse(
                    token,
                    new UserResponseDTO(user)
            );

        } catch (BadCredentialsException e) {
            throw new InvalidPasswordException("CPF ou senha inválidos");
        }
    }

    public AuthResponse loginAdmin(AuthDTO auth) {
        try {
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
        } catch (BadCredentialsException e) {
            throw new InvalidPasswordException("CPF ou senha inválidos");
        }
    }

    public void recoveryPassword(String cpf) {
        try {
            User user = repository.getByCpf(cpf).orElseThrow(() -> new UserNotFoundException("Usuário não existe"));

            String code = String.valueOf(100000 + new Random().nextInt(900000));

            ValidateCodeRequest passwordRecovery = new ValidateCodeRequest(code, user.getCpf(), user.getEmail(), user.getNome());

            redisTemplate.opsForList().leftPush(
                    "recovery_queue",
                    passwordRecovery
            );

            redisTemplate.opsForValue().set(
                    "recovery:" + cpf,
                    passwordRecovery,
                    Duration.ofMinutes(15)
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public String verificarCode(ValidateCodeRequest passwordRecovery) {
        try {
            ValidateCodeRequest recovery = (ValidateCodeRequest) redisTemplate
                    .opsForValue()
                    .get("recovery:" + passwordRecovery.cpf());

            if (recovery == null) {
                throw new ExpiredCodeException("Código expirado");
            }

            if (!recovery.code().equals(passwordRecovery.code())) {
                throw new InvalidCodeException("Código inválido");
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
                throw new ExpiredTokenException();
            }

            if (!token.equals(request.token())) {
                throw new InvalidTokenException();
            }

            User user = findByCpf(request.cpf());

            user.setSenha(passwordEncoder.encode(request.novaSenha()));

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
                throw new DuplicateCpfException();
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
                throw new DuplicateCpfException();
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
            User user = getUser(id);

            user.setFotoPerfil(fileStorageService.salvarArquivo(file));

            repository.save(user);

            return true;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void alterarSenha(ChangePasswordRequest request) {
        try {
            User user = getUser(request.userId());

            if (!passwordEncoder.matches(request.oldPassword(), user.getSenha())) {
                throw new InvalidPasswordException("Senha atual incorreta");
            }

            user.setSenha(passwordEncoder.encode(request.newPassword()));

            repository.save(user);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void atualizarUsuario(Long id, UserUpdate request) {

        User user = getUser(id);

        if (request.nome() != null) {
            user.setNome(request.nome());
        }

        if (request.sobrenome() != null) {
            user.setSobrenome(request.sobrenome());
        }

        if (request.email() != null) {
            user.setEmail(request.email());
        }

        if (request.telefone() != null) {
            user.setTelefone(request.telefone());
        }

        if (request.ativo() != null) {
            user.setAtivo(request.ativo());
        }


        if (request.condominioId() != null) {
            Condominio condominio = findById(request.condominioId());
            user.setCondominio(condominio);
        }

        user.setUpdatedAt(LocalDateTime.now());

        repository.save(user);
    }

    public Condominio findById(Long id) {
        return condominioRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Condominio não encontrado"));
    }

    public void sendCode(ValidateEmailRequest email) {
        String code = String.valueOf(100000 + new Random().nextInt(900000));

        email.setCode(code);

        redisTemplate.opsForList().leftPush("email_validation_queue", email);

        redisTemplate.opsForValue().set("email_validation_queue:" + email.getEmail(), code, Duration.ofMinutes(15));
    }

    public void verificarCode(ValidateEmailRequest emailValidate) {
        try {
            String recovery = (String) redisTemplate.opsForValue().get("email_validation_queue:" + emailValidate.getEmail());

            if (recovery == null) {
                throw new ExpiredCodeException("Código expirado");
            }

            if (!recovery.equals(emailValidate.getCode())) {
                throw new InvalidCodeException("Código inválido");
            }

            redisTemplate.delete(
                    "email_validation_queue:" + emailValidate.getEmail()
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
