package com.pickbit.jobpostingservice.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * 공고 상세 조회 응답 DTO (상세 내용 포함)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobPostingResponse(
        Long id,
        String title,
        String company,
        String description,
        int viewCount,
        LocalDateTime createdAt
) {
}