package com.jefiro.app247.infra.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jefiro.app247.domain.model.dto.ValidateCodeRequest;

import com.jefiro.app247.domain.model.dto.ValidateEmailRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailWorker {
    @Autowired
    RedisTemplate<String, Object> redisTemplate;
    @Autowired
    EmailService sender;

    @Scheduled(fixedDelay = 2000)
    public void processQueue() {
        ValidateCodeRequest passwordRecovery =
                (ValidateCodeRequest) redisTemplate.opsForList()
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

    @Scheduled(fixedDelay = 2000)
    public void sender() {
        ValidateEmailRequest emailRequest = (ValidateEmailRequest) redisTemplate.opsForList().rightPop("email_validation_queue");

        if (emailRequest == null) return;

        try {
            sender.enviarEmail(emailRequest);
        } catch (Exception e) {
            redisTemplate.opsForList().leftPush("email_validation_queue", emailRequest);
            e.printStackTrace();
        }
    }
}
