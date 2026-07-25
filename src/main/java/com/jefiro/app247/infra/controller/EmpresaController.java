package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.infra.service.EmpresaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class EmpresaController {

    @Autowired
    EmpresaService empresaService;

    @PostMapping
    public Empresa newEmpresa(Empresa empresa) {
        return empresaService.newEmpresa(empresa);
    }
}
