package com.jefiro.app247.infra.service.admin;

import com.jefiro.app247.domain.model.dto.admin.AdminOnboardingStatusResponse;
import com.jefiro.app247.infra.repository.CondominioRepository;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.repository.EstoqueCondominioRepository;
import com.jefiro.app247.infra.repository.ProdutoRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import com.jefiro.app247.infra.service.EmpresaContext;
import com.jefiro.app247.infra.service.MercadoPagoSetupStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOnboardingService {
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private CondominioRepository condominioRepository;
    @Autowired private TerminalRepository terminalRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private EstoqueCondominioRepository estoqueRepository;
    @Autowired private MercadoPagoSetupStatusService mercadoPagoSetupStatusService;

    @Transactional(readOnly = true)
    public AdminOnboardingStatusResponse status() {
        String empresaId = EmpresaContext.require();
        var mercadoPago = mercadoPagoSetupStatusService.consultar();
        return new AdminOnboardingStatusResponse(
                empresaRepository.existsById(empresaId),
                condominioRepository.existsByEmpresaIdAndAtivoTrue(empresaId),
                terminalRepository.existsByCondominioEmpresaIdAndAtivoTrue(empresaId),
                mercadoPago.contaVinculada(),
                mercadoPago.maquininhaVinculada(),
                produtoRepository.existsByEmpresaIdAndAtivoTrue(empresaId),
                estoqueRepository.existsByEmpresaIdAndAtivoTrueAndProdutoAtivoTrue(empresaId)
        );
    }
}
