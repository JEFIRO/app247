package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Promocao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PromocaoRepository extends JpaRepository<Promocao, String> {
    Optional<Promocao> findByIdPromocaoAndEmpresaId(String id, String empresaId);

    List<Promocao> findDistinctByEmpresaIdOrderByCreatedAtDesc(String empresaId);

    @Query("select distinct p from Promocao p join fetch p.produtos pp " +
            "where p.empresa.id=:empresaId and pp.produto.idProduto=:produtoId " +
            "and p.ativo=true and p.inicio<=:agora and p.fim>:agora " +
            "and (p.abrangencia=com.jefiro.app247.domain.model.enum_type.AbrangenciaPromocao.EMPRESA " +
            "or (p.abrangencia=com.jefiro.app247.domain.model.enum_type.AbrangenciaPromocao.CONDOMINIO " +
            "and p.condominio.idCondominio=:condominioId))")
    List<Promocao> findAplicaveis(@Param("empresaId") String empresaId,
                                  @Param("condominioId") String condominioId,
                                  @Param("produtoId") String produtoId,
                                  @Param("agora") LocalDateTime agora);

    @Query("select distinct p from Promocao p join fetch p.produtos pp " +
            "where p.ativo=true and ((p.inicio>:inicio and p.inicio<=:fim) " +
            "or (p.fim>:inicio and p.fim<=:fim))")
    List<Promocao> findTransicoes(@Param("inicio") LocalDateTime inicio,
                                  @Param("fim") LocalDateTime fim);
}
