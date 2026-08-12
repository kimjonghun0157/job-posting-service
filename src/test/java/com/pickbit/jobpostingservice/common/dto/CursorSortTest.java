package com.pickbit.jobpostingservice.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CursorSort 단위 테스트 — 기본값, 방향 변환, null 필드 검증
 */
class CursorSortTest {

    /**
     * 기본 정렬은 id DESC
     */
    @Test
    @DisplayName("기본 정렬은 id 내림차순이다")
    void defaultSortIsIdDesc() {
        // given & when
        CursorSort sort = CursorSort.NEWEST;

        // then
        assertThat(sort.field()).isEqualTo("id");
        assertThat(sort.direction()).isEqualTo(CursorSort.Direction.DESC);
    }

    /**
     * Direction.from은 null/빈 문자열을 DESC로 변환
     */
    @Test
    @DisplayName("방향이 null이면 DESC로 기본 적용된다")
    void directionFromNullDefaultsToDesc() {
        // given & when & then
        assertThat(CursorSort.Direction.from(null)).isEqualTo(CursorSort.Direction.DESC);
        assertThat(CursorSort.Direction.from("")).isEqualTo(CursorSort.Direction.DESC);
        assertThat(CursorSort.Direction.from("  ")).isEqualTo(CursorSort.Direction.DESC);
    }

    /**
     * Direction.from은 대소문자 무관하게 변환
     */
    @Test
    @DisplayName("방향 문자열은 대소문자를 구분하지 않는다")
    void directionFromIsCaseInsensitive() {
        // given & when & then
        assertThat(CursorSort.Direction.from("asc")).isEqualTo(CursorSort.Direction.ASC);
        assertThat(CursorSort.Direction.from("Desc")).isEqualTo(CursorSort.Direction.DESC);
    }

    /**
     * 필드가 null이면 NPE 발생
     */
    @Test
    @DisplayName("정렬 필드가 null이면 예외가 발생한다")
    void nullFieldThrowsException() {
        // given & when & then
        assertThatThrownBy(() -> new CursorSort(null, CursorSort.Direction.DESC))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("지원하지 않는 정렬 필드를 지정하면 예외가 발생한다")
    void unsupportedSortFieldThrowsException() {
        // given & when & then
        assertThatThrownBy(() -> new CursorSort("createdAt", CursorSort.Direction.ASC))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * CursorRequest에 정렬 미지정 시 기본값 적용
     */
    @Test
    @DisplayName("정렬 기준을 지정하지 않으면 id DESC가 기본 적용된다")
    void cursorRequestDefaultsToNewest() {
        // given
        CursorRequest request = new CursorRequest(null, 10);

        // when
        CursorSort sort = request.toCursorSort();

        // then
        assertThat(sort.field()).isEqualTo("id");
        assertThat(sort.direction()).isEqualTo(CursorSort.Direction.DESC);
    }
}
