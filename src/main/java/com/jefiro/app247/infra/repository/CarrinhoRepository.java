package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Carrinho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface CarrinhoRepository extends JpaRepository<Carrinho, String> {
    Optional<Carrinho> findByIdCarrinhoAndEmpresaId(String idCarrinho, String empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Carrinho c where c.idCarrinho = :carrinhoId")
    Optional<Carrinho> findByIdForUpdate(@Param("carrinhoId") String carrinhoId);
}
