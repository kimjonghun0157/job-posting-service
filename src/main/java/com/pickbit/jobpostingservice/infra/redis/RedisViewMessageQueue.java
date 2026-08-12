package com.pickbit.jobpostingservice.infra.redis;

import com.pickbit.jobpostingservice.domain.port.ViewMessageQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis List 기반 조회 이력 메시지 큐 (RPUSH/LPOP)
 */
@Component
@RequiredArgsConstructor
public class RedisViewMessageQueue implements ViewMessageQueue {

    private static final String QUEUE_KEY = "view:queue";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void push(String message) {
        redisTemplate.opsForList().rightPush(QUEUE_KEY, message);
    }

    @Override
    public String poll() {
        return redisTemplate.opsForList().leftPop(QUEUE_KEY);
    }

    @Override
    public Long size() {
        return redisTemplate.opsForList().size(QUEUE_KEY);
    }
}
