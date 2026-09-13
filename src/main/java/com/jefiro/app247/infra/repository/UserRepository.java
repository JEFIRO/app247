package com.jefiro.app247.infra.repository;


import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.OrderDTO;
import com.jefiro.app247.infra.security.SecurityIdentity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);

    UserDetails findByCpf(String cpf);

    @Query("""
            select new com.jefiro.app247.infra.security.SecurityIdentity(
                u, e.id, u.ativo, e.ativo, e.encerradaEm
            )
            from User u
            left join u.empresa e
            where u.cpf = :cpf
            """)
    Optional<SecurityIdentity> findSecurityIdentityByCpf(@Param("cpf") String cpf);

    @Query("SELECT u FROM User u WHERE u.cpf = :cpf")
    Optional<User> getByCpf(@Param("cpf") String cpf);

    boolean existsByCpf(String cpf);

  @Query("""
    SELECT new com.jefiro.app247.domain.model.dto.OrderDTO(
        o.idOrder,
        o.status,
        o.subtotal,
        o.desconto,
        o.totalCobrado,
        o.createdAt
    )
    FROM Order o
    WHERE o.user.idUser = :userId
""")
Page<OrderDTO> findOrdersByUserId(@Param("userId") String userId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.ativo=false where u.empresa.id=:empresaId")
    int deactivateAllByEmpresaId(@Param("empresaId") String empresaId);

}
