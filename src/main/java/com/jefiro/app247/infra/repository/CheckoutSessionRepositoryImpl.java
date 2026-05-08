package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.dto.CheckoutSession;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
public class CheckoutSessionRepositoryImpl implements CheckoutSessionRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    public CheckoutSessionRepositoryImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String key(String id) {
        return "session:" + id;
    }

    @Override
    public void save(CheckoutSession session) {
        redisTemplate.opsForValue()
                .set(key(session.getSessionId()), session, Duration.ofMinutes(15));
    }

    @Override
    public CheckoutSession findById(String id) {
        return (CheckoutSession) redisTemplate.opsForValue().get(key(id));
    }

    @Override
    public void delete(String id) {
        redisTemplate.delete(key(id));
    }
}