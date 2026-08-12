package com.pickbit.jobpostingservice.domain.serivce;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis INCR 기반 조회수 카운트 및 큐 적재
 */
@Service
@RequiredArgsConstructor
public class ViewCountRedisService {

    private static final int MAX_VIEW_COUNT = 100;
    private static final String VIEW_COUNT_KEY_PREFIX = "view:count:";
    private static final String VIEW_QUEUE_KEY = "view:queue";

    private final StringRedisTemplate redisTemplate;

    /**
     * Redis INCR로 카운트 확인 후 100 이하면 큐에 적재
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
        return true;
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