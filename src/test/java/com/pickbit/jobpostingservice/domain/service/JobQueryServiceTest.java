package com.pickbit.jobpostingservice.domain.service;

import com.pickbit.jobpostingservice.TestSupport;
import com.pickbit.jobpostingservice.api.dto.CursorRequest;
import com.pickbit.jobpostingservice.api.dto.JobPostingListResponse;
import com.pickbit.jobpostingservice.api.dto.SliceResponse;
import com.pickbit.jobpostingservice.domain.entity.JobPosting;
import com.pickbit.jobpostingservice.domain.entity.JobRepository;
import com.pickbit.jobpostingservice.domain.serivce.JobQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "scheduler.job-posting.fixed-rate=999999999")
class JobQueryServiceTest extends TestSupport {

    @Autowired
    private JobQueryService jobQueryService;

    @Autowired
    private JobRepository jobRepository;

    @BeforeEach
    void setUp() {
        List<JobPosting> postings = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            postings.add(JobPosting.builder()
                    .title("공고 " + i)
                    .company("기업 " + i)
                    .description("상세 내용 " + i)
                    .build());
        }
        jobRepository.saveAll(postings);
    }

    @AfterEach
    void tearDown() {
        jobRepository.deleteAllInBatch();
    }

    /**
     * cursorId 없이 조회 시 최신순 size건 조회 및 hasNext 반환
     */
    @Test
    @DisplayName("첫 페이지 조회 시 최신 공고 목록과 다음 페이지 여부를 반환한다")
    void getFirstPageReturnsLatestPostingsWithHasNext() {
        // given
        CursorRequest request = new CursorRequest(null, 10);

        // when
        SliceResponse<JobPostingListResponse> response = jobQueryService.getJobPostings(request);

        // then
        assertThat(response.content()).hasSize(10);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.lastId()).isNotNull();
        assertThat(response.content().getFirst().id())
                .isGreaterThan(response.content().getLast().id());
    }

    /**
     * cursorId 기준으로 이전 데이터를 이어서 조회
     */
    @Test
    @DisplayName("커서 ID로 다음 페이지를 조회하면 해당 ID보다 이전 공고를 반환한다")
    void getNextPageByCursorReturnsOlderPostings() {
        // given
        SliceResponse<JobPostingListResponse> firstPage = jobQueryService.getJobPostings(new CursorRequest(null, 10));
        CursorRequest request = new CursorRequest(firstPage.lastId(), 10);

        // when
        SliceResponse<JobPostingListResponse> secondPage = jobQueryService.getJobPostings(request);

        // then
        assertThat(secondPage.content()).hasSize(10);
        assertThat(secondPage.content().getFirst().id()).isLessThan(firstPage.lastId());
        assertThat(secondPage.hasNext()).isTrue();
    }

    /**
     * 마지막 페이지 조회 시 hasNext가 false
     */
    @Test
    @DisplayName("마지막 페이지를 조회하면 다음 페이지가 없음을 반환한다")
    void getLastPageReturnsHasNextFalse() {
        // given
        SliceResponse<JobPostingListResponse> first = jobQueryService.getJobPostings(new CursorRequest(null, 10));
        SliceResponse<JobPostingListResponse> second = jobQueryService.getJobPostings(new CursorRequest(first.lastId(), 10));
        CursorRequest request = new CursorRequest(second.lastId(), 10);

        // when
        SliceResponse<JobPostingListResponse> lastPage = jobQueryService.getJobPostings(request);

        // then
        assertThat(lastPage.content()).hasSize(10);
        assertThat(lastPage.hasNext()).isFalse();
    }

    /**
     * 데이터 없을 때 빈 목록과 hasNext false 반환
     */
    @Test
    @DisplayName("등록된 공고가 없으면 빈 목록을 반환한다")
    void getPostingsWhenEmptyReturnsEmptyList() {
        // given
        jobRepository.deleteAllInBatch();
        CursorRequest request = new CursorRequest(null, 10);

        // when
        SliceResponse<JobPostingListResponse> response = jobQueryService.getJobPostings(request);

        // then
        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.lastId()).isNull();
    }
}