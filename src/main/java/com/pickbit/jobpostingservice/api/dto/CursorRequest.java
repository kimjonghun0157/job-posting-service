package com.pickbit.jobpostingservice.api.dto;

/**
 * 커서 기반 페이지네이션 요청 — cursorId 이전 데이터를 size건 조회
 */
public record CursorRequest(
        Long cursorId,
        int size
) {
    private static final int DEFAULT_SIZE = 20;

    public CursorRequest {
        if (size <= 0) {
            size = DEFAULT_SIZE;
        }
    }
}