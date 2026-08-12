package com.pickbit.jobpostingservice.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JobPostingListResponse(
        Long id,
        String title,
        String company,
        int viewCount,
        LocalDateTime createdAt
) {
}