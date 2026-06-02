package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.auth.Endereco;
import com.jefiro.app247.domain.model.dto.EnderecoDTO;
import com.jefiro.app247.infra.repository.EnderecoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EnderecoService {

    @Autowired
    EnderecoRepository repository;

    public Endereco endereco(EnderecoDTO enderecoDTO) {
        return repository.save(new Endereco(enderecoDTO));
    }

}
