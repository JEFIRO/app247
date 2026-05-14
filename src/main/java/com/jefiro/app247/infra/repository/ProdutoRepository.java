package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {


    Optional<Produto> findByCodigo(String codigo);
}
