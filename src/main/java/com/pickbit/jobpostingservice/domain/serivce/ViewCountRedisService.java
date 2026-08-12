package com.pickbit.jobpostingservice.domain.serivce;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Redis 기반 조회수 카운트, 큐 적재, 메인 페이지 랭킹 관리
 */
@Service
@RequiredArgsConstructor
public class ViewCountRedisService {

    private static final int MAX_VIEW_COUNT = 100;
    private static final String VIEW_COUNT_KEY_PREFIX = "view:count:";
    private static final String VIEW_QUEUE_KEY = "view:queue";
    private static final String MAIN_RANKING_KEY = "main:ranking";
    private static final String MAIN_CACHE_KEY = "main:cache";

    private final StringRedisTemplate redisTemplate;

    /**
     * Redis INCR로 카운트 확인 후 100 이하면 큐에 적재 + 랭킹 Sorted Set 점수 증가
     */
    public boolean registerView(Long jobPostingId, Long userId) {
        String countKey = VIEW_COUNT_KEY_PREFIX + jobPostingId;
        Long count = redisTemplate.opsForValue().increment(countKey);

        if (count == null || count > MAX_VIEW_COUNT) {
            if (count != null) {
                redisTemplate.opsForValue().decrement(countKey);
            }
            return false;
        }

        String message = jobPostingId + ":" + userId + ":" + count;
        redisTemplate.opsForList().rightPush(VIEW_QUEUE_KEY, message);
        redisTemplate.opsForZSet().incrementScore(MAIN_RANKING_KEY, String.valueOf(jobPostingId), 1);
        return true;
    }

    /**
     * Sorted Set에서 상위 100건 ID 조회
     */
    public List<Long> getTopRanking(int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(MAIN_RANKING_KEY, 0, limit - 1);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }
        return tuples.stream()
                .map(t -> Long.parseLong(Objects.requireNonNull(t.getValue())))
                .toList();
    }

    /**
     * 랭킹 Sorted Set 초기화 (10분 주기 갱신 후)
     */
    public void resetRanking() {
        redisTemplate.delete(MAIN_RANKING_KEY);
    }

    /**
     * 메인 캐시에 ID 목록 저장
     */
    public void cacheMainIds(List<Long> ids) {
        redisTemplate.delete(MAIN_CACHE_KEY);
        if (!ids.isEmpty()) {
            String[] values = ids.stream().map(String::valueOf).toArray(String[]::new);
            redisTemplate.opsForList().rightPushAll(MAIN_CACHE_KEY, values);
        }
    }

    /**
     * 메인 캐시에서 ID 목록 조회
     */
    public List<Long> getCachedMainIds() {
        List<String> ids = redisTemplate.opsForList().range(MAIN_CACHE_KEY, 0, -1);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream().map(Long::parseLong).toList();
    }

    /**
     * 특정 공고의 실시간 조회수 (Redis 기준)
     */
    public int getViewCount(Long jobPostingId) {
        String value = redisTemplate.opsForValue().get(VIEW_COUNT_KEY_PREFIX + jobPostingId);
        return value == null ? 0 : Integer.parseInt(value);
    }

    /**
     * 큐에서 메시지 1건 꺼내기 (LPOP)
     */
    public String pollQueue() {
        return redisTemplate.opsForList().leftPop(VIEW_QUEUE_KEY);
    }

    /**
     * 큐에 남아있는 메시지 수 조회
     */
    public Long getQueueSize() {
        return redisTemplate.opsForList().size(VIEW_QUEUE_KEY);
    }
}