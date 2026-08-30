package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.PromocaoProduto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PromocaoProdutoRepository extends JpaRepository<PromocaoProduto, String> {
    @Query("select distinct pp.produto.idProduto from PromocaoProduto pp " +
            "where pp.promocao.empresa.id=:empresaId and pp.promocao.ativo=true " +
            "and (pp.promocao.abrangencia=com.jefiro.app247.domain.model.enum_type.AbrangenciaPromocao.EMPRESA " +
            "or (pp.promocao.abrangencia=com.jefiro.app247.domain.model.enum_type.AbrangenciaPromocao.CONDOMINIO " +
            "and pp.promocao.condominio.idCondominio=:condominioId)) " +
            "and ((pp.promocao.inicio>:lastSync and pp.promocao.inicio<=:syncAt) " +
            "or (pp.promocao.fim>:lastSync and pp.promocao.fim<=:syncAt))")
    List<String> findProductIdsWithTemporalTransition(
            @Param("empresaId") String empresaId,
            @Param("condominioId") String condominioId,
            @Param("lastSync") LocalDateTime lastSync,
            @Param("syncAt") LocalDateTime syncAt);
}
