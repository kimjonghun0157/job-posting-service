package com.pickbit.jobpostingservice.api.dto;

/**
 * 메인 페이지 공고 응답 (제목 + 실시간 조회수)
 */
public record MainJobPostingResponse(
        Long id,
        String title,
        int viewCount
) {
}
