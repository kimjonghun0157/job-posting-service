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
        if (ids.isEmpty()) {
            redisTemplate.delete(CACHE_KEY);
            return;
        }
        String tempKey = CACHE_KEY + ":tmp";
        String[] values = ids.stream().map(String::valueOf).toArray(String[]::new);
        redisTemplate.opsForList().rightPushAll(tempKey, values);
        redisTemplate.rename(tempKey, CACHE_KEY);
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
