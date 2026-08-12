package com.pickbit.jobpostingservice.common.dto;

import java.util.Objects;

/**
 * 커서 기반 페이지네이션 정렬 기준 — 허용된 필드명과 방향을 캡슐화하여
 * Pageable의 무제한 Sort 대신 제한된 정렬 옵션을 제공한다.
 */
public record CursorSort(String field, Direction direction) {

    private static final String ALLOWED_FIELD = "id";

    public enum Direction {
        ASC, DESC;

        public static Direction from(String value) {
            if (value == null || value.isBlank()) return DESC;
            return valueOf(value.toUpperCase());
        }
    }

    public static final CursorSort NEWEST = new CursorSort("id", Direction.DESC);

    public CursorSort {
        Objects.requireNonNull(field, "sort field must not be null");
        if (!ALLOWED_FIELD.equals(field)) {
            throw new IllegalArgumentException("지원하지 않는 정렬 필드: " + field + " (허용: id)");
        }
        if (direction == null) direction = Direction.DESC;
    }
}
