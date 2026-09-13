package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.dto.EmpresaRequest;
import com.jefiro.app247.domain.model.dto.EmpresaResponse;
import com.jefiro.app247.domain.model.dto.EmpresaBrandingRequest;
import com.jefiro.app247.domain.model.dto.EmpresaBrandingResponse;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmpresaService {

    @Autowired
    EmpresaRepository repository;
    @Autowired
    MercadoPagoAccountLifecycleService mercadoPagoLifecycleService;
    @Autowired
    TerminalLifecycleService terminalLifecycleService;
    @Autowired
    TerminalPointBindingService pointBindingService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    AuditLogService auditLogService;

    public Empresa newEmpresa(Empresa empresa) {
        return repository.save(empresa);
    }

    public Empresa getEmpresa(String s) {
        return repository.findById(s).orElseThrow();
    }

    @Transactional
    public Empresa getEmpresaOperacionalForUpdate(String empresaId) {
        Empresa empresa = repository.findByIdForUpdate(empresaId)
                .orElseThrow(() -> new ApiBusinessException(
                        HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Empresa não encontrada"));
        if (!Boolean.TRUE.equals(empresa.getAtivo()) || empresa.isDefinitivamenteEncerrada()) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "COMPANY_NOT_ACTIVE",
                    "A Empresa não está ativa");
        }
        return empresa;
    }

    @Transactional
    public void encerrarDefinitivamente(User gestor) {
        if (gestor == null || gestor.getRole() != RoleUser.ADMIN || gestor.getEmpresa() == null
                || !EmpresaContext.require().equals(gestor.getEmpresa().getId())) {
            throw new ApiBusinessException(HttpStatus.FORBIDDEN, "COMPANY_CLOSE_FORBIDDEN",
                    "Somente um administrador da Empresa pode encerrá-la");
        }
        Empresa empresa = repository.findByIdForUpdate(gestor.getEmpresa().getId())
                .orElseThrow(() -> new ApiBusinessException(
                        HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Empresa não encontrada"));
        if (empresa.isDefinitivamenteEncerrada()) return;

        mercadoPagoLifecycleService.validarSemPagamentosAtivos(empresa.getId());
        mercadoPagoLifecycleService.desvincularParaEncerramento(empresa);
        pointBindingService.desvincularDaEmpresa(empresa.getId(), "COMPANY_CLOSED");
        terminalLifecycleService.marcarResetRequired(empresa, "COMPANY_CLOSED");

        empresa.setAtivo(false);
        empresa.setEncerradaEm(java.time.Instant.now());
        repository.save(empresa);
        auditLogService.record(empresa, "COMPANY_DEACTIVATED", "Empresa", empresa.getId(),
                java.util.Map.of("ativo", true),
                java.util.Map.of("ativo", false, "definitive", true), java.util.Map.of());
        userRepository.deactivateAllByEmpresaId(empresa.getId());
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

    @Transactional(readOnly = true)
    public EmpresaBrandingResponse branding() {
        return EmpresaBrandingResponse.from(getEmpresa(EmpresaContext.require()));
    }

    @Transactional
    public EmpresaBrandingResponse atualizarBranding(EmpresaBrandingRequest request) {
        Empresa empresa = getEmpresa(EmpresaContext.require());
        empresa.setNomeExibicao(request.nomeExibicao().trim());
        empresa.setLogoUrl(normalizarUrl(request.logoUrl()));
        empresa.setLogoDarkUrl(normalizarUrl(request.logoDarkUrl()));
        empresa.setCorPrincipal(request.corPrincipal().toUpperCase());
        empresa.setCorSecundaria(request.corSecundaria().toUpperCase());
        empresa.setCorDestaque(request.corDestaque().toUpperCase());
        return EmpresaBrandingResponse.from(repository.save(empresa));
    }

    private String normalizarUrl(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validarTenant(String empresaId) {
        if (!EmpresaContext.require().equals(empresaId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Empresa não encontrada");
        }
    }
}
