package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface EmpresaRepository extends JpaRepository<Empresa,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Empresa e where e.id=:id")
    Optional<Empresa> findByIdForUpdate(@Param("id") String id);
}
