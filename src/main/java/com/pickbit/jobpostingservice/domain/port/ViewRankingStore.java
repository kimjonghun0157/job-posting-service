package com.pickbit.jobpostingservice.domain.port;

import java.util.List;

/**
 * 조회 트렌딩 랭킹 저장소 — 점수 가산/상위 조회/초기화
 */
public interface ViewRankingStore {

    void incrementScore(Long jobPostingId);

    List<Long> getTopIds(int limit);

    void reset();
}
