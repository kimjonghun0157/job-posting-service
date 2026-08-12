package com.pickbit.jobpostingservice.domain.scheduler;

import com.pickbit.jobpostingservice.TestSupport;
import com.pickbit.jobpostingservice.domain.entity.JobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class JobPostingSchedulerTest extends TestSupport {

    @Autowired
    private JobPostingScheduler jobPostingScheduler;

    @Autowired
    private JobRepository jobRepository;

    /**
     * autoRegister() 호출 시 job_posting 테이블에 50건 batch insert
     */
    @Test
    @DisplayName("공고 자동 등록을 실행하면 50건의 채용 공고가 등록된다")
    @Transactional
    void autoRegisterInserts50JobPostings() {
        // given
        long beforeCount = jobRepository.count();

        // when
        jobPostingScheduler.autoRegister();

        // then
        long afterCount = jobRepository.count();
        assertThat(afterCount - beforeCount).isEqualTo(50);
    }
}