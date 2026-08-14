package com.pickbit.jobpostingservice.infra.redis;

import com.pickbit.jobpostingservice.domain.dto.MainPageCacheEntry;
import com.pickbit.jobpostingservice.domain.port.MainPageCache;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis 기반 메인 페이지 캐시
 *
 * - main:cache — List, 공고 ID (노출 순서 보존)
 * - main:title — Hash, field=공고 ID / value=제목
 *
 * 제목까지 캐시에 담아 조회 경로에서 DB 접근을 없앤다.
 */
@Component
@RequiredArgsConstructor
public class RedisMainPageCache implements MainPageCache {

    private static final String CACHE_KEY = "main:cache";
    private static final String TITLE_KEY = "main:title";
    private static final String TMP_SUFFIX = ":tmp";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void cache(List<MainPageCacheEntry> entries) {
        if (entries.isEmpty()) {
            redisTemplate.delete(List.of(CACHE_KEY, TITLE_KEY));
            return;
        }

        String[] ids = entries.stream()
                .map(e -> String.valueOf(e.id()))
                .toArray(String[]::new);

        Map<String, String> titles = new LinkedHashMap<>();
        for (MainPageCacheEntry entry : entries) {
            titles.put(String.valueOf(entry.id()), entry.title());
        }

        // 임시 키에 채운 뒤 rename 으로 교체 — 갱신 중에도 조회가 반쪽 상태를 보지 않게 한다
        String cacheTmp = CACHE_KEY + TMP_SUFFIX;
        String titleTmp = TITLE_KEY + TMP_SUFFIX;

        redisTemplate.delete(List.of(cacheTmp, titleTmp));
        redisTemplate.opsForList().rightPushAll(cacheTmp, ids);
        redisTemplate.opsForHash().putAll(titleTmp, titles);
        redisTemplate.rename(cacheTmp, CACHE_KEY);
        redisTemplate.rename(titleTmp, TITLE_KEY);
    }

    @Override
    public List<MainPageCacheEntry> getCached() {
        List<String> ids = redisTemplate.opsForList().range(CACHE_KEY, 0, -1);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        // HMGET 한 번으로 제목 전체를 가져온다 (건당 왕복 없음)
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        List<String> titles = hashOps.multiGet(TITLE_KEY, ids);

        List<MainPageCacheEntry> entries = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            String title = titles.get(i);
            if (title == null) {
                continue;   // 제목이 없는 ID(삭제된 공고 등)는 노출하지 않는다
            }
            entries.add(new MainPageCacheEntry(Long.parseLong(ids.get(i)), title));
        }
        return entries;
    }
}
