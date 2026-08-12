package com.pickbit.jobpostingservice.domain.scheduler;

import com.pickbit.jobpostingservice.domain.service.JobPostingAutoCreateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 공고 자동 등록 스케줄러 — 5분 주기로 50건 batch insert 실행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobPostingScheduler {

    private final JobPostingAutoCreateService jobPostingAutoCreateService;

    @Scheduled(fixedRateString = "${scheduler.job-posting.fixed-rate:300000}")
    public void autoRegister() {
        jobPostingAutoCreateService.createBulkJobPostings();
        log.info("공고 50건 자동 등록 완료");
    }
}