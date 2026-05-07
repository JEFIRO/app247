package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Carrinho;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarrinhoRepository extends JpaRepository<Carrinho, String> {
}
