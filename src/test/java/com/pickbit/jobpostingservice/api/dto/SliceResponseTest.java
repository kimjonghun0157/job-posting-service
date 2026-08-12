package com.pickbit.jobpostingservice.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SliceResponse 단위 테스트 — hasNext 판단, 커서 ID 추출, 빈 결과 처리 검증
 */
class SliceResponseTest {

    record Item(Long id, String name) {}

    /**
     * 결과가 size보다 클 때 hasNext true, content는 size만큼만 반환
     */
    @Test
    @DisplayName("요청 크기보다 결과가 많으면 다음 페이지가 존재한다")
    void hasNextTrueWhenResultExceedsSize() {
        // given
        List<Item> items = IntStream.rangeClosed(1, 11)
                .mapToObj(i -> new Item((long) i, "item" + i))
                .toList();

        // when
        SliceResponse<Item> response = SliceResponse.of(items, 10, Item::id);

        // then
        assertThat(response.content()).hasSize(10);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.lastId()).isEqualTo(10L);
    }

    /**
     * 결과가 size 이하일 때 hasNext false, content 전체 반환
     */
    @Test
    @DisplayName("요청 크기 이하의 결과가 반환되면 다음 페이지가 없다")
    void hasNextFalseWhenResultFitsInSize() {
        // given
        List<Item> items = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> new Item((long) i, "item" + i))
                .toList();

        // when
        SliceResponse<Item> response = SliceResponse.of(items, 10, Item::id);

        // then
        assertThat(response.content()).hasSize(5);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.lastId()).isEqualTo(5L);
    }

    /**
     * 빈 결과일 때 hasNext false, lastId null 반환
     */
    @Test
    @DisplayName("결과가 없으면 빈 목록과 null 커서를 반환한다")
    void emptyResultReturnsNullLastId() {
        // given
        List<Item> items = Collections.emptyList();

        // when
        SliceResponse<Item> response = SliceResponse.of(items, 10, Item::id);

        // then
        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.lastId()).isNull();
    }
}