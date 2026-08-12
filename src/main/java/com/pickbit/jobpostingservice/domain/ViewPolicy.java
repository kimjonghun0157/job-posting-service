package com.pickbit.jobpostingservice.domain;

/**
 * 조회 관련 도메인 정책 상수 — 조회수 상한, 배치 크기, 메인 페이지 노출 수, 고유 조회 TTL
 */
public final class ViewPolicy {

    /** 공고당 최대 조회수 — 1,000명 동시 접속에도 이 값을 초과하지 않아야 한다 */
    public static final int MAX_VIEW_COUNT = 100;

    /** 큐 → DB 일괄 반영 시 한 번에 처리하는 이력 건수 */
    public static final int BATCH_FLUSH_SIZE = 500;

    /** 메인 페이지에 노출하는 최대 공고 수 */
    public static final int MAIN_PAGE_LIMIT = 100;

    /** 유저별 고유 조회 판별 키의 Redis TTL (분) — 랭킹 갱신 주기(10분)보다 여유 있게 설정 */
    public static final int UNIQUE_VIEW_TTL_MINUTES = 15;

    private ViewPolicy() {
    }
}
