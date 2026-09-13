package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.ImportacaoProdutoStatus;
import com.jefiro.app247.domain.model.enum_type.ModoImportacaoProduto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "importacao_produto")
public class ImportacaoProduto {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false) private Empresa empresa;
    @Column(name = "nome_arquivo", nullable = false, length = 255) private String nomeArquivo;
    @Column(name = "hash_arquivo", nullable = false, length = 64) private String hashArquivo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private ImportacaoProdutoStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private ModoImportacaoProduto modo = ModoImportacaoProduto.SOMENTE_NOVOS;
    @Column(name = "total_linhas", nullable = false) private int totalLinhas;
    @Column(name = "total_validas", nullable = false) private int totalValidas;
    @Column(name = "total_avisos", nullable = false) private int totalAvisos;
    @Column(name = "total_erros", nullable = false) private int totalErros;
    @Column(name = "produtos_importados", nullable = false) private int produtosImportados;
    @Column(name = "barcodes_importados", nullable = false) private int barcodesImportados;
    @Column(name = "estoques_criados", nullable = false) private int estoquesCriados;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by") private User createdBy;
    @Version @Column(name = "lock_version", nullable = false) private Long lockVersion;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "validated_at", nullable = false) private Instant validatedAt;
    @Column(name = "processed_at") private Instant processedAt;

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (validatedAt == null) validatedAt = now;
        if (modo == null) modo = ModoImportacaoProduto.SOMENTE_NOVOS;
    }
}
