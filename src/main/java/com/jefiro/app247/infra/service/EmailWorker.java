package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.dto.PasswordRecovery;
import com.jefiro.app247.infra.event.UserCreatedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class EmailWorker {
    @Autowired
    RedisTemplate<String, Object> redisTemplate;
    @Autowired
    EmailService sender;

    @Scheduled(fixedDelay = 2000)
    public void processQueue() {
        PasswordRecovery passwordRecovery =
                (PasswordRecovery) redisTemplate.opsForList()
                        .rightPop("recovery_queue");


        if (passwordRecovery == null) return;


        try {
            sender.enviarEmail(passwordRecovery);
        } catch (Exception e) {

            redisTemplate.opsForList()
                    .leftPush("recovery_queue", passwordRecovery);
            e.printStackTrace();
        }
    }
}
