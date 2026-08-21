package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.dto.TerminalResponseDTO;
import com.jefiro.app247.domain.model.dto.mercadopago.ListaTerminaisResponse;
import com.jefiro.app247.domain.model.dto.mercadopago.TerminalResponse;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.dto.mercadopago.MercadoPagoTerminalResponse;
import com.jefiro.app247.infra.repository.TerminalRepository;
import com.jefiro.app247.infra.exception.ExternalServiceException;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;

@Service
public class MercadoPagoTerminalService {
    private static final String TERMINAIS_URL = "https://api.mercadopago.com/terminals/v1/list";

    private final OauthMercadoPagoService oauthService;
    private final TerminalService terminalService;
    private final TerminalRepository terminalRepository;
    private final RestTemplate restTemplate;

    public MercadoPagoTerminalService(OauthMercadoPagoService oauthService,
                                      TerminalService terminalService,
                                      TerminalRepository terminalRepository,
                                      RestTemplate restTemplate) {
        this.oauthService = oauthService;
        this.terminalService = terminalService;
        this.terminalRepository = terminalRepository;
        this.restTemplate = restTemplate;
    }

    public List<MercadoPagoTerminalResponse> listar() {
        String empresaId = EmpresaContext.require();
        MercadoPagoConta conta = oauthService.getByEmpresa(empresaId);
        List<TerminalResponse> externos = listarExternos(conta);
        Map<String, Terminal> vinculados = terminalRepository
                .findAllByCondominioEmpresaIdAndMercadoPagoTerminalIdIsNotNull(empresaId)
                .stream()
                .collect(Collectors.toMap(Terminal::getMercadoPagoTerminalId, Function.identity()));

        return externos.stream()
                .map(externo -> new MercadoPagoTerminalResponse(externo,
                        vinculados.containsKey(externo.id())
                                ? vinculados.get(externo.id()).getIdTerminal() : null))
                .toList();
    }

    @Transactional
    public TerminalResponseDTO vincular(String terminalIdInterno, String mercadoPagoTerminalId) {
        String empresaId = EmpresaContext.require();
        MercadoPagoConta conta = oauthService.getByEmpresa(empresaId);
        Terminal terminal = terminalService.getTerminalDoTenant(terminalIdInterno);

        boolean pertenceAConta = listarExternos(conta).stream()
                .anyMatch(externo -> mercadoPagoTerminalId.equals(externo.id()));
        if (!pertenceAConta) {
            throw new IllegalArgumentException("Terminal Mercado Pago não pertence à conta da empresa");
        }

        terminalRepository.findByMercadoPagoTerminalId(mercadoPagoTerminalId)
                .filter(outro -> !outro.getIdTerminal().equals(terminalIdInterno))
                .ifPresent(outro -> {
                    throw new IllegalStateException("Maquininha Mercado Pago já vinculada a outro terminal interno");
                });

        terminal.setMercadoPagoTerminalId(mercadoPagoTerminalId);
        return new TerminalResponseDTO(terminalRepository.save(terminal));
    }

    @Transactional
    public void desvincular(String terminalIdInterno) {
        oauthService.getByEmpresa(EmpresaContext.require());
        Terminal terminal = terminalService.getTerminalDoTenant(terminalIdInterno);
        terminal.setMercadoPagoTerminalId(null);
        terminalRepository.save(terminal);
    }

    private List<TerminalResponse> listarExternos(MercadoPagoConta conta) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(conta.getAccessToken());
        ResponseEntity<ListaTerminaisResponse> response;
        try {
            response = restTemplate.exchange(
                    TERMINAIS_URL,
                    HttpMethod.GET,
                    new HttpEntity<Void>(headers),
                    ListaTerminaisResponse.class
            );
        } catch (RestClientException e) {
            throw new ExternalServiceException("Mercado Pago",
                    "Falha ao listar terminais", e);
        }
        ListaTerminaisResponse body = response.getBody();
        if (body == null || body.data() == null || body.data().terminals() == null) {
            return Collections.emptyList();
        }
        return body.data().terminals();
    }
}
