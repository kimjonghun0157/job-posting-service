package com.pickbit.jobpostingservice.infra.redis;

import com.pickbit.jobpostingservice.domain.ViewPolicy;
import com.pickbit.jobpostingservice.domain.port.UniqueViewStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis Set(SADD) 기반 유저별 고유 조회 판별 — 원자적 중복 체크 + TTL 자동 만료
 */
@Component
@RequiredArgsConstructor
public class RedisUniqueViewStore implements UniqueViewStore {

    private static final String KEY_PREFIX = "view:unique:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isFirstView(Long jobPostingId, Long userId) {
        String key = KEY_PREFIX + jobPostingId;
        Long added = redisTemplate.opsForSet().add(key, userId.toString());
        redisTemplate.expire(key, Duration.ofMinutes(ViewPolicy.UNIQUE_VIEW_TTL_MINUTES));
        return added != null && added > 0;
    }
}
