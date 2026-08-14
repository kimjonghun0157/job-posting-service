package com.pickbit.jobpostingservice.domain.dto;

/**
 * 메인 페이지 캐시 엔트리 — ID, 제목
 * 조회수는 실시간 값이라 캐시에 담지 않고 조회 시점에 별도로 읽는다.
 */
public record MainPageCacheEntry(
        Long id,
        String title
) {
}
