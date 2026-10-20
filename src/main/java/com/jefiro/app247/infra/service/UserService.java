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
import com.jefiro.app247.infra.repository.OrderRepository;
import com.jefiro.app247.infra.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.NoSuchElementException;

@Service
public class UserService {
    private static final Set<String> ORDER_SORTS = Set.of("createdAt", "status", "totalCobrado");

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    @Autowired
    RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private UserRepository repository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    AuthenticationManager authenticationManager;
    @Autowired
    TokenService tokenService;
    @Autowired
    private ApplicationEventPublisher publisher;
    @Autowired
    ObjectStorageService objectStorageService;
    @Autowired
    CondominioRepository condominioRepository;
    @Autowired
    EmpresaService empresaService;
    @Autowired
    CpfService cpfService;

    public User getUser(String user) {
        return repository.findById(user).orElseThrow(UserNotFoundException::new);
    }

    public AuthResponse login(AuthDTO auth) {

        try {
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(
                            cpfService.requireValid(auth.cpf()),
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
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(cpfService.requireValid(auth.cpf()), auth.senha());

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

    public Page<OrderDTO> getOrderByUser(User target, Pageable pageable) {
        if (target.getEmpresa() == null) return Page.empty(pageable);
        return orderRepository.findOrderDtosByUserAndEmpresa(
                target.getIdUser(), target.getEmpresa().getId(),
                PagePolicy.sanitize(pageable, Sort.unsorted(), ORDER_SORTS, "Ordenação de compras inválida"));
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
    public boolean salvarFoto(MultipartFile file, User user) {
        String anterior = user.getFotoPerfil();
        user.setFotoPerfil(objectStorageService.uploadImage(file, "usuarios/" + user.getIdUser()));
        repository.save(user);
        objectStorageService.deleteAfterCommit(anterior);

        return true;
    }

    public void alterarSenha(User user, ChangePasswordRequest request) {

        if (!passwordEncoder.matches(request.oldPassword(), user.getSenha())) {
            throw new InvalidPasswordException("Senha atual incorreta");
        }
        if (request.newPassword().equals(request.oldPassword())) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED",
                    "A nova senha deve ser diferente da atual");
        }
        user.setSenha(passwordEncoder.encode(request.newPassword()));

        repository.save(user);
    }

    /**
     * Gestor edita usuários do próprio tenant, mas nunca o e-mail de terceiros: e-mail é o
     * destino da recuperação de senha, e trocá-lo equivale a tomar a conta. GERENTE não
     * altera ADMIN, e {@code ativo} só vale para terceiros (ignorado na própria conta).
     */
    public void atualizarUsuario(User actor, User user, UserUpdate request) {
        boolean self = actor == user
                || (actor.getIdUser() != null && actor.getIdUser().equals(user.getIdUser()));
        boolean manager = actor.getRole() == RoleUser.ADMIN || actor.getRole() == RoleUser.GERENTE;

        if (!self && actor.getRole() == RoleUser.GERENTE && user.getRole() == RoleUser.ADMIN) {
            throw new ApiBusinessException(HttpStatus.FORBIDDEN, "USER_MANAGEMENT_FORBIDDEN",
                    "Gerente não pode alterar um administrador");
        }

        String email = request.email() == null ? null : request.email().trim().toLowerCase(Locale.ROOT);
        boolean emailChanged = email != null && !email.equalsIgnoreCase(user.getEmail());
        if (emailChanged && !self) {
            throw new ApiBusinessException(HttpStatus.FORBIDDEN, "EMAIL_CHANGE_SELF_ONLY",
                    "Somente o próprio usuário pode alterar o e-mail");
        }
        if (emailChanged && repository.existsByEmailIgnoreCase(email)) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                    "Já existe uma conta cadastrada com este e-mail.");
        }

        if (request.nome() != null) {
            user.setNome(request.nome().trim());
        }

        if (request.sobrenome() != null) {
            user.setSobrenome(request.sobrenome().trim());
        }

        if (emailChanged) {
            user.setEmail(email);
            user.setEmailVerificado(false);
        }

        if (request.telefone() != null) {
            user.setTelefone(request.telefone().replaceAll("\\D", ""));
        }

        if (manager && !self && request.ativo() != null) {
            user.setAtivo(request.ativo());
        }

        if (manager && request.condominioId() != null) {
            Condominio condominio = findById(request.condominioId());
            user.setCondominio(condominio);
        }

        user.setUpdatedAt(java.time.Instant.now());

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

    public void invalidarCode(String email) {
        redisTemplate.delete("email_validation_queue:" + email);
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
            user.setUpdatedAt(java.time.Instant.now());
            repository.save(user);
        });

    }
}
