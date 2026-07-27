package com.jefiro.app247.infra.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PaymentWorker {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private PagamentoService pagamentoService;

    @Scheduled(fixedDelay = 2000)
    public void processQueue() {
        String json = redisTemplate.opsForList().rightPop("mp_queue");
        if (json == null || json.isBlank()) {
            return; 
        }

        try {
            pagamentoService.atualizarPagamento(json);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Mensagem: " + e.getMessage());
            System.out.println("Classe: " + e.getClass().getName());
            redisTemplate.opsForList().leftPush("mp_queue", json);
        }
    }
}