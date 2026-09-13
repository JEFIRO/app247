package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.infra.dto.mercadopago.MercadoPagoSetupStatusResponse;
import com.jefiro.app247.infra.repository.OauthMercadoPagoRepository;
import com.jefiro.app247.domain.model.enum_type.MercadoPagoAccountBindingStatus;
import com.jefiro.app247.domain.model.enum_type.MercadoPagoSetupState;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class MercadoPagoSetupStatusService {
    private final OauthMercadoPagoRepository contaRepository;
    private final TerminalRepository terminalRepository;
    private final MercadoPagoAccountLifecycleService accountLifecycleService;

    public MercadoPagoSetupStatusService(OauthMercadoPagoRepository contaRepository,
                                         TerminalRepository terminalRepository,
                                         MercadoPagoAccountLifecycleService accountLifecycleService) {
        this.contaRepository = contaRepository;
        this.terminalRepository = terminalRepository;
        this.accountLifecycleService = accountLifecycleService;
    }

    @Transactional(readOnly = true)
    public MercadoPagoSetupStatusResponse consultar() {
        String empresaId = EmpresaContext.require();
        Optional<MercadoPagoConta> conta = accountLifecycleService.findAtivaPorEmpresa(empresaId);
        boolean contaVinculada = conta
                .map(this::isAutorizacaoValida)
                .orElse(false);
        long quantidadeTerminais = terminalRepository.countByCondominioEmpresaId(empresaId);
        long quantidadeMaquininhas = terminalRepository
                .countByCondominioEmpresaIdAndMercadoPagoTerminalIdIsNotNull(empresaId);
        boolean maquininhaVinculada = contaVinculada && quantidadeMaquininhas > 0;
        MercadoPagoSetupState contaStatus = resolverEstado(empresaId, conta,
                contaVinculada, maquininhaVinculada);

        return new MercadoPagoSetupStatusResponse(
                contaVinculada,
                maquininhaVinculada,
                contaVinculada && maquininhaVinculada,
                quantidadeTerminais,
                quantidadeMaquininhas,
                conta.map(MercadoPagoConta::getLinkedAt).orElse(null),
                contaStatus
        );
    }

    private MercadoPagoSetupState resolverEstado(String empresaId, Optional<MercadoPagoConta> conta,
                                                  boolean contaVinculada,
                                                  boolean maquininhaVinculada) {
        if (conta.isPresent()) {
            if (conta.get().getStatus() == MercadoPagoAccountBindingStatus.REVOKED) {
                return MercadoPagoSetupState.CONTA_REVOGADA;
            }
            if (conta.get().getStatus() == MercadoPagoAccountBindingStatus.ERROR || !contaVinculada) {
                return MercadoPagoSetupState.CONTA_COM_ERRO;
            }
            return maquininhaVinculada ? MercadoPagoSetupState.POINT_CONFIGURADA
                    : MercadoPagoSetupState.CONTA_VINCULADA_SEM_POINT;
        }
        Optional<MercadoPagoConta> latest = contaRepository
                .findFirstByEmpresaIdOrderByLinkedAtDesc(empresaId);
        if (latest.map(MercadoPagoConta::getStatus).orElse(null)
                == MercadoPagoAccountBindingStatus.REVOKED) {
            return MercadoPagoSetupState.CONTA_REVOGADA;
        }
        if (latest.map(MercadoPagoConta::getStatus).orElse(null)
                == MercadoPagoAccountBindingStatus.ERROR) {
            return MercadoPagoSetupState.CONTA_COM_ERRO;
        }
        return MercadoPagoSetupState.CONTA_NAO_VINCULADA;
    }

    private boolean isAutorizacaoValida(MercadoPagoConta conta) {
        return conta.getAccessToken() != null
                && !conta.getAccessToken().isBlank()
                && conta.getDataExpiracao() != null
                && conta.getDataExpiracao().isAfter(Instant.now());
    }
}
