package com.pickbit.jobpostingservice.api.dto;

import java.util.List;
import java.util.function.Function;

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