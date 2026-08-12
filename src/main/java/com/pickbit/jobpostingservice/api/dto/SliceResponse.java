package com.pickbit.jobpostingservice.api.dto;

import java.util.List;
import java.util.function.Function;

/**
 * 커서 기반 슬라이스 응답 — content 목록, 다음 페이지 존재 여부, 마지막 커서 ID 포함
 */
public record SliceResponse<T>(
        List<T> content,
        boolean hasNext,
        Long lastId
) {
    public static <T> SliceResponse<T> of(List<T> result, int size, Function<T, Long> idExtractor) {
        boolean hasNext = result.size() > size;
        List<T> content = hasNext ? result.subList(0, size) : result;
        Long lastId = content.isEmpty() ? null : idExtractor.apply(content.getLast());

        return new SliceResponse<>(content, hasNext, lastId);
    }
}