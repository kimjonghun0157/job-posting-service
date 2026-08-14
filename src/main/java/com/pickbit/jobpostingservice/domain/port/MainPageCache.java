package com.pickbit.jobpostingservice.domain.port;

import com.pickbit.jobpostingservice.domain.dto.MainPageCacheEntry;

import java.util.List;

/**
 * 메인 페이지 노출 공고 캐시 — ID와 제목을 함께 보관해 조회 시 DB 접근이 필요 없게 한다
 */
public interface MainPageCache {

    void cache(List<MainPageCacheEntry> entries);

    List<MainPageCacheEntry> getCached();
}
