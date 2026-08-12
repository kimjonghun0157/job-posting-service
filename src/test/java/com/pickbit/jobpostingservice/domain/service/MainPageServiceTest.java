package com.pickbit.jobpostingservice.domain.service;

import com.pickbit.jobpostingservice.TestSupport;
import com.pickbit.jobpostingservice.api.dto.MainJobPostingResponse;
import com.pickbit.jobpostingservice.domain.entity.JobPosting;
import com.pickbit.jobpostingservice.domain.entity.JobRepository;
import com.pickbit.jobpostingservice.domain.serivce.MainPageService;
import com.pickbit.jobpostingservice.domain.serivce.ViewCountRedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 메인 페이지 서비스 테스트 — 랭킹 기반 노출, fallback, 실시간 조회수, 주기 초기화 검증
 */
@TestPropertySource(properties = "scheduler.job-posting.fixed-rate=999999999")
class MainPageServiceTest extends TestSupport {

    @Autowired
    private MainPageService mainPageService;

    @Autowired
    private ViewCountRedisService viewCountRedisService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private List<JobPosting> savedPostings;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAllInBatch();
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();

        List<JobPosting> postings = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            postings.add(JobPosting.builder()
                    .title("공고 " + i)
                    .company("기업 " + i)
                    .description("상세 " + i)
                    .build());
        }
        savedPostings = jobRepository.saveAll(postings);
    }

    /**
     * 조회가 많은 공고 순으로 메인 페이지에 노출
     */
    @Test
    @DisplayName("조회가 많은 공고가 메인 페이지 상위에 노출된다")
    void mainPageShowsMostViewedPostingsFirst() {
        // given
        JobPosting hot = savedPostings.get(2);
        JobPosting warm = savedPostings.get(0);
        for (int i = 0; i < 10; i++) viewCountRedisService.registerView(hot.getId(), (long) i);
        for (int i = 0; i < 3; i++) viewCountRedisService.registerView(warm.getId(), (long) i);

        // when
        mainPageService.refreshMainPage();
        List<MainJobPostingResponse> result = mainPageService.getMainPagePostings();

        // then
        assertThat(result).isNotEmpty();
        assertThat(result.getFirst().id()).isEqualTo(hot.getId());
    }

    /**
     * 조회 이력이 없으면 최신순 fallback
     */
    @Test
    @DisplayName("조회 이력이 없으면 최신 공고순으로 메인에 노출된다")
    void mainPageFallsBackToLatestWhenNoViews() {
        // given — 조회 없음

        // when
        mainPageService.refreshMainPage();
        List<MainJobPostingResponse> result = mainPageService.getMainPagePostings();

        // then
        assertThat(result).hasSize(5);
    }

    /**
     * 조회수는 Redis 실시간 값 반영
     */
    @Test
    @DisplayName("메인 페이지의 조회수는 실시간으로 반영된다")
    void mainPageViewCountIsRealTime() {
        // given
        JobPosting posting = savedPostings.getFirst();
        for (int i = 0; i < 5; i++) viewCountRedisService.registerView(posting.getId(), (long) i);
        mainPageService.refreshMainPage();

        // when — 추가 조회 발생
        for (int i = 5; i < 8; i++) viewCountRedisService.registerView(posting.getId(), (long) i);
        List<MainJobPostingResponse> result = mainPageService.getMainPagePostings();

        // then
        MainJobPostingResponse target = result.stream()
                .filter(r -> r.id().equals(posting.getId()))
                .findFirst().orElseThrow();
        assertThat(target.viewCount()).isEqualTo(8);
    }

    /**
     * refreshMainPage 후 랭킹이 초기화되는지 확인
     */
    @Test
    @DisplayName("갱신 후 랭킹이 초기화되어 다음 주기에 새로 집계된다")
    void refreshResetsRanking() {
        // given
        JobPosting posting = savedPostings.getFirst();
        for (int i = 0; i < 5; i++) viewCountRedisService.registerView(posting.getId(), (long) i);

        // when
        mainPageService.refreshMainPage();
        List<Long> rankingAfterRefresh = viewCountRedisService.getTopRanking(100);

        // then
        assertThat(rankingAfterRefresh).isEmpty();
    }
}
