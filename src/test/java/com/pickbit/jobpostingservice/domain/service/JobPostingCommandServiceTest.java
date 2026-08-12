package com.pickbit.jobpostingservice.domain.service;

import com.pickbit.jobpostingservice.TestSupport;
import com.pickbit.jobpostingservice.domain.entity.JobPosting;
import com.pickbit.jobpostingservice.domain.entity.JobRepository;
import com.pickbit.jobpostingservice.domain.serivce.JobPostingAutoCreateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공고 대량 생성 서비스 테스트 — 50건 batch insert 및 필드 무결성 검증
 */
@TestPropertySource(properties = "scheduler.job-posting.fixed-rate=999999999")
class JobPostingAutoCreateServiceTest extends TestSupport {

    @Autowired
    private JobPostingAutoCreateService jobPostingAutoCreateService;

    @Autowired
    private JobRepository jobRepository;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAllInBatch();
    }

    /**
     * createBulkJobPostings() 호출 시 job_posting 테이블에 50건 batch insert
     */
    @Test
    @DisplayName("공고 대량 등록을 실행하면 50건의 채용 공고가 저장된다")
    void createBulkJobPostingsInserts50Records() {
        // given — setUp에서 전체 삭제 완료

        // when
        jobPostingAutoCreateService.createBulkJobPostings();

        // then
        assertThat(jobRepository.count()).isEqualTo(50);
    }

    /**
     * 생성된 공고의 제목, 기업명, 상세내용이 빈 값 없이 저장되는지 검증
     */
    @Test
    @DisplayName("등록된 공고는 제목, 기업명, 상세내용이 모두 존재한다")
    void createdPostingsHaveAllFields() {
        // given
        jobPostingAutoCreateService.createBulkJobPostings();

        // when
        List<JobPosting> postings = jobRepository.findAll();

        // then
        assertThat(postings).allSatisfy(posting -> {
            assertThat(posting.getTitle()).isNotBlank();
            assertThat(posting.getCompany()).isNotBlank();
            assertThat(posting.getDescription()).isNotBlank();
            assertThat(posting.getViewCount()).isZero();
        });
    }
}