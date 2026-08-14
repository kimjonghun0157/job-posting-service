package com.pickbit.jobpostingservice.domain.service;

import com.pickbit.jobpostingservice.TestSupport;
import com.pickbit.jobpostingservice.domain.dto.MainPagePostingReadModel;
import com.pickbit.jobpostingservice.domain.entity.JobPosting;
import com.pickbit.jobpostingservice.domain.port.ViewRankingStore;
import com.pickbit.jobpostingservice.domain.repository.JobRepository;
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
@TestPropertySource(properties = {
        "scheduler.job-posting.fixed-rate=999999999",
        "scheduler.view-history.fixed-rate=999999999",
        "scheduler.main-page.fixed-rate=999999999"
})
class MainPageServiceTest extends TestSupport {

    @Autowired
    private MainPageService mainPageService;

    @Autowired
    private ViewRegistrationService viewRegistrationService;

    @Autowired
    private ViewRankingStore viewRankingStore;

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
        for (int i = 0; i < 10; i++) viewRegistrationService.registerView(hot.getId(), (long) i);
        for (int i = 0; i < 3; i++) viewRegistrationService.registerView(warm.getId(), (long) i);

        // when
        mainPageService.refreshMainPage();
        List<MainPagePostingReadModel> result = mainPageService.getMainPagePostings();

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
        List<MainPagePostingReadModel> result = mainPageService.getMainPagePostings();

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
        for (int i = 0; i < 5; i++) viewRegistrationService.registerView(posting.getId(), (long) i);
        mainPageService.refreshMainPage();

        // when — 추가 조회 발생
        for (int i = 5; i < 8; i++) viewRegistrationService.registerView(posting.getId(), (long) i);
        List<MainPagePostingReadModel> result = mainPageService.getMainPagePostings();

        // then
        MainPagePostingReadModel target = result.stream()
                .filter(r -> r.id().equals(posting.getId()))
                .findFirst().orElseThrow();
        assertThat(target.viewCount()).isEqualTo(8);
    }

    /**
     * 같은 유저의 반복 조회는 랭킹에 1회만 반영
     */
    @Test
    @DisplayName("같은 유저가 같은 공고를 반복 조회해도 랭킹 점수는 1만 오른다")
    void repeatedViewsBySameUserCountsOnceInRanking() {
        // given
        JobPosting target = savedPostings.get(0);
        JobPosting other = savedPostings.get(1);
        Long sameUserId = 1L;

        for (int i = 0; i < 20; i++) viewRegistrationService.registerView(target.getId(), sameUserId);
        for (int i = 0; i < 3; i++) viewRegistrationService.registerView(other.getId(), (long) (i + 10));

        // when
        mainPageService.refreshMainPage();
        List<MainPagePostingReadModel> result = mainPageService.getMainPagePostings();

        // then — other는 고유 유저 3명, target은 고유 유저 1명 → other가 상위
        assertThat(result).isNotEmpty();
        assertThat(result.getFirst().id()).isEqualTo(other.getId());
    }

    /**
     * 서로 다른 유저의 조회는 각각 랭킹에 반영
     */
    @Test
    @DisplayName("서로 다른 유저가 조회하면 각각 랭킹에 반영된다")
    void distinctUsersEachContributeToRanking() {
        // given
        JobPosting posting = savedPostings.get(0);
        for (int i = 0; i < 5; i++) viewRegistrationService.registerView(posting.getId(), (long) i);

        // when
        List<Long> topIds = viewRankingStore.getTopIds(10);

        // then — 고유 유저 5명 → 랭킹 점수 5
        assertThat(topIds).containsExactly(posting.getId());
    }

    /**
     * 제목은 갱신 시점에 캐시에 담기므로 조회 시 DB를 읽지 않는다
     */
    @Test
    @DisplayName("메인 페이지 제목은 DB 재조회 없이 캐시에서 반환된다")
    void mainPageTitleComesFromCacheWithoutDbAccess() {
        // given — 갱신으로 제목까지 캐시에 적재
        mainPageService.refreshMainPage();
        String expectedTitle = savedPostings.getFirst().getTitle();

        // when — DB를 비운 뒤 조회
        jobRepository.deleteAllInBatch();
        List<MainPagePostingReadModel> result = mainPageService.getMainPagePostings();

        // then — DB가 비었어도 캐시의 제목이 그대로 나온다
        assertThat(result).hasSize(5);
        assertThat(result).extracting(MainPagePostingReadModel::title).contains(expectedTitle);
        assertThat(result).noneMatch(r -> r.title() == null || r.title().isBlank());
    }

    /**
     * refreshMainPage 후 랭킹이 초기화되는지 확인
     */
    @Test
    @DisplayName("갱신 후 랭킹이 초기화되어 다음 주기에 새로 집계된다")
    void refreshResetsRanking() {
        // given
        JobPosting posting = savedPostings.getFirst();
        for (int i = 0; i < 5; i++) viewRegistrationService.registerView(posting.getId(), (long) i);

        // when
        mainPageService.refreshMainPage();
        List<Long> rankingAfterRefresh = viewRankingStore.getTopIds(100);

        // then
        assertThat(rankingAfterRefresh).isEmpty();
    }
}
