package com.pickbit.jobpostingservice.domain.dto;

/**
 * 메인 페이지 공고 도메인 읽기 모델 — ID, 제목, 실시간 조회수
 */
public record MainPagePostingReadModel(
        Long id,
        String title,
        int viewCount
) {
}
