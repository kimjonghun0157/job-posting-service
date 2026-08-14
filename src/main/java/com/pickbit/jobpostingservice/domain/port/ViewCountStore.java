package com.pickbit.jobpostingservice.domain.port;

import java.util.List;

/**
 * 조회수 원자적 카운팅 저장소 — INCR/DECR/GET
 */
public interface ViewCountStore {

    Long increment(Long jobPostingId);

    void decrement(Long jobPostingId);

    int getCount(Long jobPostingId);

    /**
     * 여러 공고의 조회수를 한 번에 조회한다 (MGET).
     * 메인 페이지처럼 최대 100건을 읽는 경로에서 건당 왕복을 없애기 위한 벌크 조회.
     * 값이 없는 ID는 0으로 채우며, 반환 순서는 인자로 받은 ID 순서와 동일하다.
     */
    List<Integer> getCounts(List<Long> jobPostingIds);
}
