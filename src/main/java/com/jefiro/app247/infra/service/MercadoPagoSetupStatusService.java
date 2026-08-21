package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.infra.dto.mercadopago.MercadoPagoSetupStatusResponse;
import com.jefiro.app247.infra.repository.OauthMercadoPagoRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class MercadoPagoSetupStatusService {
    private final OauthMercadoPagoRepository contaRepository;
    private final TerminalRepository terminalRepository;

    public MercadoPagoSetupStatusService(OauthMercadoPagoRepository contaRepository,
                                         TerminalRepository terminalRepository) {
        this.contaRepository = contaRepository;
        this.terminalRepository = terminalRepository;
    }

    @Transactional(readOnly = true)
    public MercadoPagoSetupStatusResponse consultar() {
        String empresaId = EmpresaContext.require();
        boolean contaVinculada = contaRepository.findByEmpresaId(empresaId)
                .map(this::isAutorizacaoValida)
                .orElse(false);
        long quantidadeTerminais = terminalRepository.countByCondominioEmpresaId(empresaId);
        long quantidadeMaquininhas = terminalRepository
                .findAllByCondominioEmpresaIdAndMercadoPagoTerminalIdIsNotNull(empresaId)
                .stream()
                .filter(terminal -> terminal.getMercadoPagoTerminalId() != null
                        && !terminal.getMercadoPagoTerminalId().isBlank())
                .count();
        boolean maquininhaVinculada = quantidadeMaquininhas > 0;

        return new MercadoPagoSetupStatusResponse(
                contaVinculada,
                maquininhaVinculada,
                contaVinculada && maquininhaVinculada,
                quantidadeTerminais,
                quantidadeMaquininhas
        );
    }

    private boolean isAutorizacaoValida(MercadoPagoConta conta) {
        return conta.getAccessToken() != null
                && !conta.getAccessToken().isBlank()
                && conta.getDataExpiracao() != null
                && conta.getDataExpiracao().isAfter(LocalDateTime.now());
    }
}
