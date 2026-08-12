package com.pickbit.jobpostingservice.domain.port;

/**
 * 조회수 원자적 카운팅 저장소 — INCR/DECR/GET
 */
public interface ViewCountStore {

    Long increment(Long jobPostingId);

    void decrement(Long jobPostingId);

    int getCount(Long jobPostingId);
}
