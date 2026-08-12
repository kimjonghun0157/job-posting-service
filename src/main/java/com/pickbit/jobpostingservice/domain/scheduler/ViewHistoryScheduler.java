package com.pickbit.jobpostingservice.domain.scheduler;

import com.pickbit.jobpostingservice.domain.service.ViewHistoryCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 조회 이력 DB 반영 스케줄러 — 1초 주기로 Redis 큐 flush
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewHistoryScheduler {

    private final ViewHistoryCommandService viewHistoryCommandService;

    @Scheduled(fixedRateString = "${scheduler.view-history.fixed-rate:1000}")
    public void flush() {
        viewHistoryCommandService.flushViewQueue();
    }
}
