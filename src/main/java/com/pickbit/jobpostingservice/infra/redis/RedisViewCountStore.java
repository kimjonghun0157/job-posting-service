package com.pickbit.jobpostingservice.infra.redis;

import com.pickbit.jobpostingservice.domain.port.ViewCountStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

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

    /**
     * MGET 단일 왕복으로 N건을 조회한다.
     * 건당 GET(왕복 N회) 대비 메인 페이지 100건 기준 왕복이 100 → 1 로 줄어든다.
     */
    @Override
    public List<Integer> getCounts(List<Long> jobPostingIds) {
        if (jobPostingIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> keys = jobPostingIds.stream().map(id -> KEY_PREFIX + id).toList();
        List<String> values = redisTemplate.opsForValue().multiGet(keys);

        if (values == null) {
            return Collections.nCopies(jobPostingIds.size(), 0);
        }
        return values.stream()
                .map(v -> v == null ? 0 : Integer.parseInt(v))
                .toList();
    }
}
