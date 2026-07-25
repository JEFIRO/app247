package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EmpresaService {

    @Autowired
    EmpresaRepository repository;

    public Empresa newEmpresa(Empresa empresa) {
        return repository.save(empresa);
    }

    public Empresa getEmpresa(String s) {
        return repository.findById(s).orElseThrow();
    }
}
