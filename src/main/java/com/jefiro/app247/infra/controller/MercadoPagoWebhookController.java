package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.mercadopago.PreferenceReturn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/webhook/mercadopago")
public class MercadoPagoWebhookController {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @PostMapping
    public ResponseEntity<String> receive(@RequestBody Map<String, Object> body) {
        
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        System.out.println(data);
        String paymentId = String.valueOf(data.get("id"));

        redisTemplate.opsForList().leftPush("mp_queue", paymentId);

        return ResponseEntity.ok("ENFILEIRADO");
    }
//       @PostMapping("checkout")
//    public ResponseEntity<String> receive(@RequestBody PreferenceReturn preferenceReturn, @RequestParam String status,@RequestParam String cliente) {
//
//        Map<String, Object> data = (Map<String, Object>) body.get("data");
//        String paymentId = String.valueOf(data.get("id"));
//
//        redisTemplate.opsForList().leftPush("mp_queue", paymentId);
//
//        return ResponseEntity.ok("ENFILEIRADO");
//    }

}