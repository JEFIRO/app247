package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, String> {
    Optional<Produto> findByCodigo(String codigo);

    List<Produto> findAllByUpdateAtAfter(LocalDateTime lastSync);

    List<Produto> findTop10ByOrderByCreateAtDesc();
}
