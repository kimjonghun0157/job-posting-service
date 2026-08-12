package com.pickbit.jobpostingservice.domain.scheduler;

import com.pickbit.jobpostingservice.domain.service.MainPageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 메인 페이지 갱신 스케줄러 — 10분 주기로 랭킹 기반 캐시 갱신
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MainPageScheduler {

    private final MainPageService mainPageService;

    @Scheduled(fixedRateString = "${scheduler.main-page.fixed-rate:600000}")
    public void refresh() {
        mainPageService.refreshMainPage();
    }
}
