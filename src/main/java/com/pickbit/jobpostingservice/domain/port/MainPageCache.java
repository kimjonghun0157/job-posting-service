package com.pickbit.jobpostingservice.domain.port;

import java.util.List;

/**
 * 메인 페이지 공고 ID 캐시 — 저장/조회
 */
public interface MainPageCache {

    void cacheIds(List<Long> ids);

    List<Long> getCachedIds();
}
