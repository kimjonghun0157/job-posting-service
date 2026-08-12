package com.pickbit.jobpostingservice.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

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