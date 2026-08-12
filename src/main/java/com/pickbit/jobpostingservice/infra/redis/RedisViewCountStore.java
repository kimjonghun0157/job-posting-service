package com.pickbit.jobpostingservice.infra.redis;

import com.pickbit.jobpostingservice.domain.port.ViewCountStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis String 기반 조회수 카운터 (INCR/DECR/GET)
 */
@Component
@RequiredArgsConstructor
public class RedisViewCountStore implements ViewCountStore {

    private static final String KEY_PREFIX = "view:count:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public Long increment(Long jobPostingId) {
        return redisTemplate.opsForValue().increment(KEY_PREFIX + jobPostingId);
    }

    @Override
    public void decrement(Long jobPostingId) {
        redisTemplate.opsForValue().decrement(KEY_PREFIX + jobPostingId);
    }

    @Override
    public int getCount(Long jobPostingId) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + jobPostingId);
        return value == null ? 0 : Integer.parseInt(value);
    }
}
