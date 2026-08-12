package com.pickbit.jobpostingservice.infra.redis;

import com.pickbit.jobpostingservice.domain.port.ViewRankingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Redis Sorted Set 기반 조회 트렌딩 랭킹 (ZINCRBY/ZREVRANGE)
 */
@Component
@RequiredArgsConstructor
public class RedisViewRankingStore implements ViewRankingStore {

    private static final String RANKING_KEY = "main:ranking";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void incrementScore(Long jobPostingId) {
        redisTemplate.opsForZSet().incrementScore(RANKING_KEY, String.valueOf(jobPostingId), 1);
    }

    @Override
    public List<Long> getTopIds(int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(RANKING_KEY, 0, limit - 1);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }
        return tuples.stream()
                .map(t -> Long.parseLong(Objects.requireNonNull(t.getValue())))
                .toList();
    }

    @Override
    public void reset() {
        redisTemplate.delete(RANKING_KEY);
    }
}
