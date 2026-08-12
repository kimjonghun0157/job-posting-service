package com.pickbit.jobpostingservice.domain.scheduler;

import com.pickbit.jobpostingservice.domain.serivce.JobPostingAutoCreateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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