package com.jefiro.app247.infra.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/webhook")
public class MercadoPagoWebhookController {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    ObjectMapper objectMapper;


    @PostMapping("/mercadopago")
    public ResponseEntity<String> receive(@RequestBody Map<String, Object> body) throws JsonProcessingException {
        Map<String, Object> data = (Map<String, Object>) body.get("data");

        if (data == null) {
            return ResponseEntity.badRequest().body("Payload sem campo 'data'");
        }

        String bodyJson = objectMapper.writeValueAsString(body);
        System.out.println("body");
        redisTemplate.opsForList().leftPush("mp_queue", bodyJson);

        return ResponseEntity.ok("ENFILEIRADO");
    }
}