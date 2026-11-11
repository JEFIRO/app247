package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.NotaFiscalEntradaStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "nota_fiscal_entrada", uniqueConstraints =
        @UniqueConstraint(name = "uk_nota_fiscal_entrada_concluida", columnNames = {"empresa_id", "chave_concluida"}))
public class NotaFiscalEntrada {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false) private Empresa empresa;
    @Column(nullable = false, length = 44) private String chave;
    @Column(name = "chave_concluida", length = 44) private String chaveConcluida;
    @Column(nullable = false, length = 2) private String modelo;
    @Column(nullable = false, length = 9) private String numero;
    @Column(nullable = false, length = 3) private String serie;
    @Column(name = "emitente_cnpj", nullable = false, length = 14) private String emitenteCnpj;
    @Column(name = "emitente_nome", nullable = false, length = 180) private String emitenteNome;
    @Column(name = "destinatario_documento", length = 14) private String destinatarioDocumento;
    @Column(name = "data_emissao") private Instant dataEmissao;
    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2) private BigDecimal valorTotal;
    @Column(nullable = false, length = 20) private String origem;
    @Column(name = "hash_xml", nullable = false, length = 64) private String hashXml;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private NotaFiscalEntradaStatus status;
    @Column(name = "produtos_criados", nullable = false) private int produtosCriados;
    @Column(name = "entradas_lancadas", nullable = false) private int entradasLancadas;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by") private User createdBy;
    @Version @Column(name = "lock_version", nullable = false) private Long lockVersion;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "processed_at") private Instant processedAt;
    @OneToMany(mappedBy = "nota", cascade = CascadeType.ALL)
    @OrderBy("numeroItem ASC")
    private List<NotaFiscalEntradaItem> itens = new ArrayList<>();

    @PrePersist void prePersist() { if (createdAt == null) createdAt = Instant.now(); }
}
