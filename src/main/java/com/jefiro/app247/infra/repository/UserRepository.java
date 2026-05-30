package com.jefiro.app247.infra.repository;


import com.jefiro.app247.domain.model.auth.User;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    UserDetails findByCpf(String cpf);

    @Query("SELECT u FROM User u WHERE u.cpf = :cpf")
    Optional<User> getByCpf(@Param("cpf") String cpf);

    boolean existsByCpf(String cpf);
}
