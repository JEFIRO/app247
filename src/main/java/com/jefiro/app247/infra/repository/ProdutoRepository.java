package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    Produto findByCodigo(String codigo);
}
