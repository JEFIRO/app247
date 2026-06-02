package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.auth.Endereco;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.CondominioRequest;
import com.jefiro.app247.domain.model.terminal.Terminal;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor

@Entity
@Table(name = "condominio")
public class Condominio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long condominioId;

    private String uuidCondominio;

    private String nome;
    private String cnpj;


    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "endereco_id")
    private Endereco endereco;

    private Boolean ativo;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "condominio")
    private List<User> users = new ArrayList<>();


    @OneToMany(
            mappedBy = "condominio",
            cascade = CascadeType.ALL
    )
    private List<Terminal> terminais = new ArrayList<>();

    public Condominio(CondominioRequest request, Endereco endereco) {
        this.uuidCondominio = UUID.randomUUID().toString();
        this.nome = request.nome();
        this.cnpj = request.cnpj();
        this.endereco = endereco;
        this.ativo = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void addUser(User user) {
        user.setCondominio(this);
        this.users.add(user);
    }

    public void addTerminal(Terminal terminal) {
        terminal.setCondominio(this);
        this.terminais.add(terminal);

    }
}
