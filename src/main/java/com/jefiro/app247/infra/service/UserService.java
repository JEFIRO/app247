package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
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
import java.security.SecureRandom;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
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
    @Autowired
    EmpresaService empresaService;

    public User getUser(String user) {
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
        repository.getByCpf(cpf).ifPresent(user -> {
            String code = String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));

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
        });

    }

    public String verificarCode(ValidateCodeRequest passwordRecovery) {
        ValidateCodeRequest recovery = (ValidateCodeRequest) redisTemplate
                .opsForValue()
                .get("recovery:" + passwordRecovery.cpf());

        if (recovery == null) {
            throw new ExpiredCodeException("Código expirado");
        }
        if (!recovery.code().equals(passwordRecovery.code())) {
            throw new InvalidCodeException("Código inválido");
        }
        redisTemplate.delete("recovery:" + passwordRecovery.cpf());

        String resetToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                "reset:" + passwordRecovery.cpf(), resetToken, 15, TimeUnit.MINUTES);

        return resetToken;
    }

    public boolean novaSenha(ResetPasswordRequest request) {
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
    }


    public Page<OrderDTO> getOrderByUser(String user_id, Pageable pageable) {
        return repository.findOrdersByUserId(user_id, pageable);
    }

    public void cadastrar(@Valid UserRequestDTO requestDTO) {
        Empresa empresa = empresaService.getEmpresa(EmpresaContext.require());
        User user = new User(requestDTO);
        user.setEmpresa(empresa);
        salvarNovoUsuario(user, RoleUser.USER);
    }

    public User cadastrarGestor(@Valid UserRequestDTO requestDTO, Empresa empresa) {
        User gestor = new User(requestDTO);
        gestor.setEmpresa(empresa);
        return salvarNovoUsuario(gestor, RoleUser.ADMIN);
    }

    private User salvarNovoUsuario(User user, RoleUser roleUser) {
        if (repository.existsByCpf(user.getCpf())) {
            throw new DuplicateCpfException();
        }
        user.setRole(roleUser);
        user.setSenha(passwordEncoder.encode(user.getSenha()));
        User salvo = repository.save(user);
        publisher.publishEvent(new UserCreatedEvent(salvo));
        return salvo;
    }

    @Transactional
    public boolean salvarFoto(MultipartFile file, String id) {
        User user = getUser(id);
        try {
            user.setFotoPerfil(fileStorageService.salvarArquivo(file));
        } catch (IOException e) {
            throw new FileStorageException("Falha ao armazenar foto do usuário", e);
        }
        repository.save(user);

        return true;
    }

    public void alterarSenha(ChangePasswordRequest request) {
        User user = getUser(request.userId());

        if (!passwordEncoder.matches(request.oldPassword(), user.getSenha())) {
            throw new InvalidPasswordException("Senha atual incorreta");
        }
        user.setSenha(passwordEncoder.encode(request.newPassword()));

        repository.save(user);
    }

    public void atualizarUsuario(String id, UserUpdate request) {

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

    public Condominio findById(String id) {
        return condominioRepository.findByIdCondominioAndEmpresaId(id, EmpresaContext.require())
                .orElseThrow(() -> new NoSuchElementException("Condominio não encontrado"));
    }

    public void sendCode(ValidateEmailRequest email) {
        String code = String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));

        email.setCode(code);

        redisTemplate.opsForList().leftPush("email_validation_queue", email);

        redisTemplate.opsForValue().set("email_validation_queue:" + email.getEmail(), code, Duration.ofMinutes(15));
    }

    @Transactional
    public void verificarCode(ValidateEmailRequest emailValidate) {
        String recovery = (String) redisTemplate.opsForValue().get("email_validation_queue:" + emailValidate.getEmail());

        if (recovery == null) {
            throw new ExpiredCodeException("Código expirado");
        }
        if (!recovery.equals(emailValidate.getCode())) {
            throw new InvalidCodeException("Código inválido");
        }
        redisTemplate.delete("email_validation_queue:" + emailValidate.getEmail());
        repository.findByEmail(emailValidate.getEmail()).ifPresent(user -> {
            user.setEmailVerificado(true);
            user.setUpdatedAt(LocalDateTime.now());
            repository.save(user);
        });

    }
}
