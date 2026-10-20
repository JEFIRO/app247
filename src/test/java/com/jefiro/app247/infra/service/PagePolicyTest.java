package com.jefiro.app247.infra.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagePolicyTest {
    private static final Set<String> SORTS = Set.of("nome", "createdAt");

    @Test
    void aceitaOrdenacaoPermitidaEPreservaDirecao() {
        Pageable result = PagePolicy.sanitize(PageRequest.of(2, 25, Sort.by(Sort.Direction.DESC, "nome")),
                Sort.unsorted(), SORTS, "inválida");

        assertThat(result.getPageNumber()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(25);
        assertThat(result.getSort().getOrderFor("nome").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void recusaCampoForaDaListaComIllegalArgument() {
        assertThatThrownBy(() -> PagePolicy.sanitize(PageRequest.of(0, 10, Sort.by("senha")),
                Sort.unsorted(), SORTS, "Ordenação inválida"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ordenação inválida");
    }

    @Test
    void recusaExpressaoDeOrdenacaoComSql() {
        assertThatThrownBy(() -> PagePolicy.sanitize(PageRequest.of(0, 10, Sort.by("nome; drop table user")),
                Sort.unsorted(), SORTS, "inválida"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void limitaTamanhoDaPaginaEUsaOrdenacaoPadrao() {
        Pageable result = PagePolicy.sanitize(PageRequest.of(0, 5000), Sort.by("createdAt"), SORTS, "inválida");

        assertThat(result.getPageSize()).isEqualTo(PagePolicy.MAX_PAGE_SIZE);
        assertThat(result.getSort()).isEqualTo(Sort.by("createdAt"));
    }

    @Test
    void semSortMantemOrdemDoBancoQuandoPadraoEhUnsorted() {
        Pageable result = PagePolicy.sanitize(PageRequest.of(0, 10), Sort.unsorted(), SORTS, "inválida");

        assertThat(result.getSort().isSorted()).isFalse();
    }
}
