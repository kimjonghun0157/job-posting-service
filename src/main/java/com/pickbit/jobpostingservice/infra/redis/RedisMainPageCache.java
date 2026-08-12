package com.pickbit.jobpostingservice.infra.redis;

import com.pickbit.jobpostingservice.domain.port.MainPageCache;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Redis List 기반 메인 페이지 공고 ID 캐시
 */
@Component
@RequiredArgsConstructor
public class RedisMainPageCache implements MainPageCache {

    private static final String CACHE_KEY = "main:cache";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void cacheIds(List<Long> ids) {
        redisTemplate.delete(CACHE_KEY);
        if (!ids.isEmpty()) {
            String[] values = ids.stream().map(String::valueOf).toArray(String[]::new);
            redisTemplate.opsForList().rightPushAll(CACHE_KEY, values);
        }
    }

    @Override
    public List<Long> getCachedIds() {
        List<String> ids = redisTemplate.opsForList().range(CACHE_KEY, 0, -1);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream().map(Long::parseLong).toList();
    }
}
