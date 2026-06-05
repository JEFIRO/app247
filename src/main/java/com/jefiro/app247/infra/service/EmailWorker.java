package com.jefiro.app247.infra.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jefiro.app247.domain.model.dto.ValidateCodeRequest;

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

//    @Scheduled(fixedDelay = 2000)
//    public void sender() {
//
//        String json = redisTemplate.opsForList().rightPop("valid_email_queue").toString();
//
//        if (json == null) return;
//
//        try {
//            Map<String, String> validEmail =
//                    objectMapper.readValue(json, new TypeReference<>() {
//                    });
//
//            sender.enviarEmail(validEmail);
//
//        } catch (Exception e) {
//
//            redisTemplate.opsForList().leftPush("recovery_queue", json);
//            e.printStackTrace();
//        }
//    }
}
