package com.pickbit.jobpostingservice.domain.serivce;

import com.pickbit.jobpostingservice.api.dto.MainJobPostingResponse;
import com.pickbit.jobpostingservice.domain.entity.JobPosting;
import com.pickbit.jobpostingservice.domain.entity.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 메인 페이지 공고 목록 관리 (10분 주기 갱신, 조회수 실시간)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MainPageService {

    private static final int MAIN_PAGE_LIMIT = 100;

    private final JobRepository jobRepository;
    private final ViewCountRedisService viewCountRedisService;

    /**
     * 10분 주기로 Sorted Set 상위 100건을 메인 캐시에 저장 후 랭킹 초기화
     */
    @Scheduled(fixedRate = 600_000)
    public void refreshMainPage() {
        List<Long> topIds = viewCountRedisService.getTopRanking(MAIN_PAGE_LIMIT);

        if (topIds.isEmpty()) {
            List<Long> fallbackIds = jobRepository.findTopByOrderByCreatedAtDesc(MAIN_PAGE_LIMIT)
                    .stream().map(JobPosting::getId).toList();
            viewCountRedisService.cacheMainIds(fallbackIds);
        } else {
            viewCountRedisService.cacheMainIds(topIds);
        }

        viewCountRedisService.resetRanking();
        log.info("메인 페이지 공고 {}건 갱신 완료", topIds.size());
    }

    /**
     * 메인 페이지 공고 목록 조회 (캐시 ID + Redis 실시간 조회수)
     */
    public List<MainJobPostingResponse> getMainPagePostings() {
        List<Long> cachedIds = viewCountRedisService.getCachedMainIds();
        if (cachedIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, JobPosting> postingMap = jobRepository.findByIdIn(cachedIds)
                .stream().collect(Collectors.toMap(JobPosting::getId, Function.identity()));

        return cachedIds.stream()
                .filter(postingMap::containsKey)
                .map(id -> {
                    JobPosting posting = postingMap.get(id);
                    int viewCount = viewCountRedisService.getViewCount(id);
                    return new MainJobPostingResponse(id, posting.getTitle(), viewCount);
                })
                .toList();
    }
}
