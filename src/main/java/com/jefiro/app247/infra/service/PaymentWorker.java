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
    private MercadoPagoService mercadoPagoService;

    @Scheduled(fixedDelay = 2000) // roda a cada 2s
    public void processQueue() {

        String paymentId = redisTemplate.opsForList()
                .rightPop("mp_queue");

        if (paymentId == null) return;

        try {
            mercadoPagoService.atualizarPagamento(paymentId);
        } catch (Exception e) {
            redisTemplate.opsForList().leftPush("mp_queue", paymentId);
        }
    }
}