package com.pickbit.jobpostingservice.api.dto;

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