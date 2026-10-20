package com.jefiro.app247.infra.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/** Política única de paginação vinda do cliente: tamanho limitado e ordenação só por campos permitidos. */
public final class PagePolicy {
    public static final int MAX_PAGE_SIZE = 100;

    private PagePolicy() {}

    /**
     * @param defaultSort  usada quando o cliente não envia sort (Sort.unsorted() preserva a ordem do banco)
     * @param allowedSorts propriedades aceitas em sort; qualquer outra gera IllegalArgumentException (HTTP 400)
     */
    public static Pageable sanitize(Pageable pageable, Sort defaultSort, Set<String> allowedSorts, String invalidSortMessage) {
        int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : defaultSort;
        if (sort.stream().anyMatch(order -> !allowedSorts.contains(order.getProperty()))) {
            throw new IllegalArgumentException(invalidSortMessage);
        }
        return PageRequest.of(Math.max(pageable.getPageNumber(), 0), size, sort);
    }
}
