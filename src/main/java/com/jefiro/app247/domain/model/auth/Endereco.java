package com.jefiro.app247.domain.model.auth;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.dto.EnderecoDTO;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

@Entity(name = "endereco")
@Table(name = "endereco")
public class Endereco {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String idEndereco;

    private String rua;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;
    private String estado;
    private String cep;
    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    public Endereco(EnderecoDTO enderecoDTO) {
        this.rua = enderecoDTO.rua();
        this.numero = enderecoDTO.numero();
        this.complemento = enderecoDTO.complemento();
        this.bairro = enderecoDTO.bairro();
        this.cidade = enderecoDTO.cidade();
        this.estado = enderecoDTO.estado();
        this.cep = enderecoDTO.cep();
    }
}
