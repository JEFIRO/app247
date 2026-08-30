package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.Endereco;
import com.jefiro.app247.domain.model.dto.CondominioRequest;
import com.jefiro.app247.domain.model.dto.CondominioResponse;
import com.jefiro.app247.infra.repository.CondominioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CondominioService {
    private final CondominioRepository repository;
    private final EmpresaService empresaService;

    public CondominioService(CondominioRepository repository, EmpresaService empresaService) {
        this.repository = repository;
        this.empresaService = empresaService;
    }

    @Transactional
    public CondominioResponse criar(CondominioRequest request) {
        String empresaId = EmpresaContext.require();
        Empresa empresa = empresaService.getEmpresa(empresaId);
        Condominio condominio = construir(request, empresa);
        return new CondominioResponse(repository.save(condominio));
    }

    public List<CondominioResponse> listar() {
        return repository.findAllByEmpresaIdOrderByNome(EmpresaContext.require()).stream()
                .map(CondominioResponse::new)
                .toList();
    }

    public CondominioResponse buscar(String condominioId) {
        return new CondominioResponse(buscarDoTenant(condominioId, EmpresaContext.require()));
    }

    @Transactional
    public CondominioResponse atualizar(String condominioId, CondominioRequest request) {
        Condominio condominio = buscarDoTenant(condominioId, EmpresaContext.require());
        condominio.setNome(request.nome());
        condominio.setCnpj(request.cnpj());
        if (request.endereco() != null) {
            Endereco endereco = new Endereco(request.endereco());
            endereco.setEmpresa(condominio.getEmpresa());
            condominio.setEndereco(endereco);
        }
        condominio.setUpdatedAt(LocalDateTime.now());
        return new CondominioResponse(repository.save(condominio));
    }

    @Transactional
    public CondominioResponse desativar(String condominioId) {
        Condominio condominio = buscarDoTenant(condominioId, EmpresaContext.require());
        condominio.setAtivo(false);
        condominio.setUpdatedAt(LocalDateTime.now());
        return new CondominioResponse(repository.save(condominio));
    }

    public Condominio buscarDoTenant(String condominioId, String empresaId) {
        return repository.findByIdCondominioAndEmpresaId(condominioId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
    }

    Condominio construir(CondominioRequest request, Empresa empresa) {
        Endereco endereco = request.endereco() == null ? null : new Endereco(request.endereco());
        if (endereco != null) {
            endereco.setEmpresa(empresa);
        }
        Condominio condominio = new Condominio(request, endereco);
        condominio.setEmpresa(empresa);
        return condominio;
    }
}
