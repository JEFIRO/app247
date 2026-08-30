package com.jefiro.app247.infra.service;

import com.jefiro.app247.infra.repository.PromocaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class PromocaoTemporalScheduler {
    @Autowired private PromocaoRepository promocaoRepository;
    @Autowired private PromocaoService promocaoService;

    private LocalDateTime ultimoCiclo = LocalDateTime.now(ZoneOffset.UTC);

    @Scheduled(fixedDelayString = "${promotions.transition-delay-ms:60000}")
    @Transactional
    public void notificarTransicoes() {
        LocalDateTime agora = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime inicio = ultimoCiclo;
        ultimoCiclo = agora;
        promocaoRepository.findTransicoes(inicio, agora)
                .forEach(promocaoService::publicarTransicao);
    }
}
