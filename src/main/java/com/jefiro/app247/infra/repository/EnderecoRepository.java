package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.auth.Endereco;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnderecoRepository extends JpaRepository<Endereco, Long> {
}
