package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.dto.EmpresaRequest;
import com.jefiro.app247.domain.model.dto.EmpresaResponse;
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

    public EmpresaResponse getEmpresaDoContexto(String empresaId) {
        validarTenant(empresaId);
        return new EmpresaResponse(getEmpresa(empresaId));
    }

    public EmpresaResponse atualizar(String empresaId, EmpresaRequest request) {
        validarTenant(empresaId);
        Empresa empresa = getEmpresa(empresaId);
        empresa.setRazaoSocial(request.razaoSocial());
        empresa.setNomeFantasia(request.nomeFantasia());
        empresa.setCnpj(request.cnpj());
        empresa.setEmail(request.email());
        empresa.setTelefone(request.telefone());
        empresa.setCep(request.cep());
        empresa.setLogradouro(request.logradouro());
        empresa.setNumero(request.numero());
        empresa.setBairro(request.bairro());
        empresa.setCidade(request.cidade());
        empresa.setEstado(request.estado());
        return new EmpresaResponse(repository.save(empresa));
    }

    private void validarTenant(String empresaId) {
        if (!EmpresaContext.require().equals(empresaId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Empresa não encontrada");
        }
    }
}
