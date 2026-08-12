package com.pickbit.jobpostingservice.domain.scheduler;

import com.pickbit.jobpostingservice.TestSupport;
import com.pickbit.jobpostingservice.domain.entity.JobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 스케줄링 주기를 3초로 설정하고 10초 대기 후 job_posting 테이블에 150건(3회 x 50건) insert 검증
 */
@TestPropertySource(properties = "scheduler.job-posting.fixed-rate=3000")
class JobPostingSchedulerSchedulingTest extends TestSupport {

    @Autowired
    private JobRepository jobRepository;

    @Test
    @DisplayName("3초 간격으로 스케줄링이 동작하여 10초 후 150건의 공고가 등록된다")
    void scheduledAutoRegisterCreates150PostingsIn10Seconds() {
        // given
        long beforeCount = jobRepository.count();

        // when & then
        await().atMost(12, SECONDS)
                .pollInterval(1, SECONDS)
                .untilAsserted(() ->
                        assertThat(jobRepository.count() - beforeCount).isGreaterThanOrEqualTo(150)
                );
    }
}