package com.pickbit.jobpostingservice.domain.dto;

import java.time.LocalDateTime;

/**
 * 공고 목록 조회 도메인 읽기 모델 — QueryDSL 프로젝션 결과
 */
public record JobPostingReadModel(
        Long id,
        String title,
        String company,
        int viewCount,
        LocalDateTime createdAt
) {
}
