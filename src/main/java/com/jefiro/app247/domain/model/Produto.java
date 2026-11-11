package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.dto.CreateProductDTO;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import com.jefiro.app247.infra.service.MoneyPolicy;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "produto", uniqueConstraints =
@UniqueConstraint(name = "uk_produto_empresa_sku", columnNames = {"empresa_id", "codigo_interno"}))
public class Produto {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String idProduto;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;
    @Column(name = "codigo_interno", nullable = false, length = 80)
    private String codigoInterno;
    @Column(nullable = false, length = 180)
    private String nome;
    @Column(length = 1000)
    private String descricao;
    @Column(name = "preco_venda", nullable = false, precision = 15, scale = 6)
    private BigDecimal precoVenda;
    @Column(precision = 15, scale = 3)
    private BigDecimal peso;
    @Column(name = "peso_tolerancia", precision = 15, scale = 3)
    private BigDecimal pesoTolerancia;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ProdutoCategoria categoria;
    @Enumerated(EnumType.STRING)
    @Column(name = "unidade_medida", nullable = false, length = 20)
    private UnidadeMedida unidadeMedida;
    @Column(length = 500)
    private String foto;
    @Column(nullable = false)
    private boolean ativo = true;
    /** Disponível em todos os Condomínios da Empresa, inclusive nos criados depois. */
    @Column(name = "disponivel_todos_condominios", nullable = false)
    private boolean disponivelTodosCondominios = false;
    /** Abaixo deste saldo o estoque central está baixo; null = sem mínimo. */
    @Column(name = "estoque_minimo_central", precision = 15, scale = 3)
    private BigDecimal estoqueMinimoCentral;
    /** Mesmo mínimo para o saldo de cada Condomínio; null = sem mínimo. */
    @Column(name = "estoque_minimo_condominio", precision = 15, scale = 3)
    private BigDecimal estoqueMinimoCondominio;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @OneToMany(mappedBy = "produto", fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("principal DESC, createdAt ASC")
    private List<ProdutoCodigoBarras> codigosBarras = new ArrayList<>();
    @OneToOne(mappedBy = "produto", fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private ProdutoFiscal fiscal;

    public Produto(CreateProductDTO dto) {
        codigoInterno = dto.codigoInternoEfetivo();
        nome = dto.nome();
        precoVenda = MoneyPolicy.persistence(dto.preco());
        unidadeMedida = UnidadeMedida.valueOf(dto.unidadeMedida().toUpperCase());
        categoria = ProdutoCategoria.valueOf(dto.categoria().toUpperCase());
        descricao = dto.descricao();
        foto = dto.foto();
        peso = dto.peso();
        pesoTolerancia = dto.pesoTolerancia();
        ativo = dto.ativo() == null || dto.ativo();
        estoqueMinimoCentral = minimoOuNull(dto.estoqueMinimoCentral());
        estoqueMinimoCondominio = minimoOuNull(dto.estoqueMinimoCondominio());
    }

    /** Zero (ou vazio) significa "sem mínimo" e é guardado como null. */
    public static BigDecimal minimoOuNull(BigDecimal valor) {
        return valor == null || valor.signum() <= 0 ? null : valor;
    }

    public boolean centralAbaixoDoMinimo(BigDecimal saldo) {
        return abaixo(saldo, estoqueMinimoCentral);
    }

    public boolean condominioAbaixoDoMinimo(BigDecimal saldo) {
        return abaixo(saldo, estoqueMinimoCondominio);
    }

    private static boolean abaixo(BigDecimal saldo, BigDecimal minimo) {
        return minimo != null && (saldo == null ? BigDecimal.ZERO : saldo).compareTo(minimo) < 0;
    }

    public void adicionarCodigoBarras(String codigo, String tipo, boolean principal) {
        adicionarCodigoBarras(codigo, tipo, principal, true);
    }

    public void adicionarCodigoBarras(String codigo, String tipo, boolean principal, boolean ativo) {
        ProdutoCodigoBarras barcode = new ProdutoCodigoBarras();
        barcode.setProduto(this);
        barcode.setEmpresa(empresa);
        barcode.setCodigoBarras(codigo);
        barcode.setTipo(tipo == null ? "INTERNO" : tipo);
        barcode.setAtivo(ativo);
        barcode.setPrincipal(ativo && principal);
        codigosBarras.add(barcode);
    }

    public String getCodigo() {
        return codigosBarras == null ? codigoInterno : codigosBarras.stream().filter(b -> Boolean.TRUE.equals(b.getAtivo()) && Boolean.TRUE.equals(b.getPrincipal())).map(ProdutoCodigoBarras::getCodigoBarras).findFirst().orElse(codigoInterno);
    }

    public void setCodigo(String codigo) {
        this.codigoInterno = codigo;
    }

    public BigDecimal getPreco() {
        return precoVenda;
    }

    public void setPreco(BigDecimal preco) {
        this.precoVenda = MoneyPolicy.persistence(preco);
    }

    public boolean isStatus() {
        return ativo;
    }

    public void setStatus(boolean status) {
        this.ativo = status;
    }

    public Instant getCreateAt() {
        return createdAt;
    }

    public void setCreateAt(Instant value) {
        createdAt = value;
    }

    public Instant getUpdateAt() {
        return updatedAt;
    }

    public void setUpdateAt(Instant value) {
        updatedAt = value;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
