package com.pickbit.jobpostingservice.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * 공고 목록 조회 응답 DTO (커서 기반 페이지네이션용, 상세 내용 제외)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobPostingListResponse(
        Long id,
        String title,
        String company,
        int viewCount,
        LocalDateTime createdAt
) {
}