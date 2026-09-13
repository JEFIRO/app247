package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.CarrinhoStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
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
@Table(name = "carrinho")
public class Carrinho {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String idCarrinho;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condominio_id", nullable = false)
    private Condominio condominio;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terminal_id", nullable = false)
    private Terminal terminal;
    @OneToMany(mappedBy = "carrinho", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CarrinhoStatus status = CarrinhoStatus.OPEN;
    @Column(nullable = false, precision = 15, scale = 6)
    private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getIdTerminal() {
        return terminal != null ? terminal.getIdTerminal() : null;
    }

    public void setTerminal(Terminal terminal) {
        this.terminal = terminal;
        if (terminal != null) {
            condominio = terminal.getCondominio();
            empresa = condominio != null ? condominio.getEmpresa() : null;
        }
    }

    public void addItem(CartItem item) {
        if (item == null) throw new IllegalArgumentException("Item não pode ser nulo");
        item.setCarrinho(this);
        if (item.getEmpresa() == null) item.setEmpresa(empresa);
        items.add(item);
    }

    public void removeItem(CartItem item) {
        if (items.remove(item)) item.setCarrinho(null);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = CarrinhoStatus.OPEN;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
