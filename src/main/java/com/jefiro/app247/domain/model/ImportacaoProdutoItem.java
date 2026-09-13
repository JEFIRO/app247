package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.ImportacaoProdutoItemStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "importacao_produto_item", uniqueConstraints =
        @UniqueConstraint(name = "uk_importacao_item_linha", columnNames = {"importacao_id", "aba", "linha"}))
public class ImportacaoProdutoItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false) private Empresa empresa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "importacao_id", nullable = false) private ImportacaoProduto importacao;
    @Column(nullable = false, length = 40) private String aba;
    @Column(nullable = false) private int linha;
    @Column(name = "codigo_interno", length = 80) private String codigoInterno;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private ImportacaoProdutoItemStatus status;
    @Column(length = 1000) private String mensagem;
    @Lob @Column(name = "dados_normalizados", columnDefinition = "longtext")
    private String dadosNormalizados;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "produto_id") private Produto produto;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "processed_at") private Instant processedAt;

    @PrePersist void prePersist() { if (createdAt == null) createdAt = Instant.now(); }
}
