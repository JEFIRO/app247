package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.auth.AuthResponse;
import com.jefiro.app247.domain.model.dto.LoginRequestDTO;
import com.jefiro.app247.domain.model.dto.UserRequestDTO;
import com.jefiro.app247.domain.model.dto.UserResponseDTO;
import com.jefiro.app247.infra.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {

    private UserRepository repository;

    public UserResponseDTO saveUser(UserRequestDTO request) {
        User user = new User(request);

        return new UserResponseDTO(repository.save(user));
    }

    public AuthResponse login(LoginRequestDTO request) {
        return new AuthResponse(UUID.randomUUID().toString(), new UserResponseDTO(repository.findByEmail(request.email()).get()));
    }


}
