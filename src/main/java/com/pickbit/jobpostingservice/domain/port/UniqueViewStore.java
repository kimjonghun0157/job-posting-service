package com.pickbit.jobpostingservice.domain.port;

/**
 * 유저별 고유 조회 판별 저장소 — 광클/봇 공격으로부터 랭킹 신뢰성 보호
 */
public interface UniqueViewStore {

    /**
     * 해당 유저의 해당 공고 첫 조회 여부를 원자적으로 판별하고 기록한다.
     *
     * @return true면 첫 조회 (랭킹 가산 허용), false면 중복 조회 (랭킹 스킵)
     */
    boolean isFirstView(Long jobPostingId, Long userId);
}
