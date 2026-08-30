package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.CarrinhoStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor

@Table(name = "carrinho")
@Entity
public class Carrinho {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String idCarrinho;
    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @OneToMany(mappedBy = "carrinho",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<Item> items = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_terminal", nullable = false)
    private Terminal terminal;

    public String getIdTerminal() {
        return terminal != null ? terminal.getIdTerminal() : null;
    }

    @Enumerated(EnumType.STRING)
    private CarrinhoStatus status;
    @Column(precision = 15, scale = 6)
    private BigDecimal subtotal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Carrinho() {
        this.status = CarrinhoStatus.OPEN;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void addItem(Item item) {
        if (item == null) {
            throw new IllegalArgumentException("Item não pode ser nulo");
        }
        if (items == null) {
            items = new ArrayList<>();
        }
        item.setCarrinho(this);
        items.add(item);
    }

    public void removeItem(Item item) {
        if (items != null && items.remove(item)) {
            item.setCarrinho(null);
        }
    }
}
