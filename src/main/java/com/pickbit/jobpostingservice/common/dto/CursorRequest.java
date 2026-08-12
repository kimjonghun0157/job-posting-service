package com.pickbit.jobpostingservice.common.dto;

/**
 * 커서 기반 페이지네이션 요청 — cursorId 이전/이후 데이터를 size건 조회하며,
 * sortBy·direction으로 정렬 기준을 지정한다.
 */
public record CursorRequest(
        Long cursorId,
        int size,
        String sortBy,
        String direction
) {
    private static final int DEFAULT_SIZE = 20;

    public CursorRequest {
        if (size <= 0) size = DEFAULT_SIZE;
    }

    public CursorRequest(Long cursorId, int size) {
        this(cursorId, size, null, null);
    }

    public CursorSort toCursorSort() {
        String field = (sortBy != null && !sortBy.isBlank()) ? sortBy : "id";
        return new CursorSort(field, CursorSort.Direction.from(direction));
    }
}
