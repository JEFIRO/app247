package com.jefiro.app247.infra.repository;


import com.jefiro.app247.domain.model.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    UserDetails findByCpf(String cpf);

    boolean existsByCpf(String cpf);
}
