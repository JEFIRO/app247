package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.Endereco;
import com.jefiro.app247.domain.model.dto.CondominioRequest;
import com.jefiro.app247.domain.model.dto.CondominioResponse;
import com.jefiro.app247.infra.repository.CondominioRepository;
import com.jefiro.app247.infra.repository.EnderecoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class CondominioService {
    private final CondominioRepository repository;
    private final EmpresaService empresaService;
    private final EnderecoRepository enderecoRepository;

    public CondominioService(CondominioRepository repository, EmpresaService empresaService,
                             EnderecoRepository enderecoRepository) {
        this.repository = repository;
        this.empresaService = empresaService;
        this.enderecoRepository = enderecoRepository;
    }

    @Transactional
    public CondominioResponse criar(CondominioRequest request) {
        String empresaId = EmpresaContext.require();
        Empresa empresa = empresaService.getEmpresa(empresaId);
        return new CondominioResponse(salvarNovo(request, empresa));
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
            Endereco endereco = condominio.getEndereco();
            if (endereco == null) {
                endereco = new Endereco();
                endereco.setEmpresa(condominio.getEmpresa());
            }
            copiarEndereco(request, endereco);
            if (endereco.getIdEndereco() == null) {
                endereco = enderecoRepository.save(endereco);
            }
            condominio.setEndereco(endereco);
        }
        condominio.setUpdatedAt(Instant.now());
        return new CondominioResponse(repository.save(condominio));
    }

    @Transactional
    public CondominioResponse desativar(String condominioId) {
        Condominio condominio = buscarDoTenant(condominioId, EmpresaContext.require());
        condominio.setAtivo(false);
        condominio.setUpdatedAt(Instant.now());
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

    /**
     * Persiste explicitamente o lado independente antes de gravar a FK de
     * condominio.endereco_id. A relação não possui cascade de propósito.
     */
    @Transactional
    public Condominio salvarNovo(CondominioRequest request, Empresa empresa) {
        Condominio condominio = construir(request, empresa);
        if (condominio.getEndereco() != null) {
            condominio.setEndereco(enderecoRepository.save(condominio.getEndereco()));
        }
        return repository.save(condominio);
    }

    private void copiarEndereco(CondominioRequest request, Endereco endereco) {
        endereco.setRua(request.endereco().rua());
        endereco.setNumero(request.endereco().numero());
        endereco.setComplemento(request.endereco().complemento());
        endereco.setBairro(request.endereco().bairro());
        endereco.setCidade(request.endereco().cidade());
        endereco.setEstado(request.endereco().estado());
        endereco.setCep(request.endereco().cep());
    }
}
