package com.pickbit.jobpostingservice.domain.service;

import com.pickbit.jobpostingservice.domain.ViewPolicy;
import com.pickbit.jobpostingservice.domain.dto.MainPagePostingReadModel;
import com.pickbit.jobpostingservice.domain.entity.JobPosting;
import com.pickbit.jobpostingservice.domain.port.MainPageCache;
import com.pickbit.jobpostingservice.domain.port.ViewCountStore;
import com.pickbit.jobpostingservice.domain.port.ViewRankingStore;
import com.pickbit.jobpostingservice.domain.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 메인 페이지 공고 목록 관리 — 랭킹 기반 캐시 갱신 + 실시간 조회수 조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MainPageService {

    private static final int MAIN_PAGE_LIMIT = ViewPolicy.MAIN_PAGE_LIMIT;

    private final JobRepository jobRepository;
    private final ViewRankingStore viewRankingStore;
    private final MainPageCache mainPageCache;
    private final ViewCountStore viewCountStore;

    /**
     * Sorted Set 상위 100건을 메인 캐시에 저장 후 랭킹 초기화
     */
    public void refreshMainPage() {
        List<Long> topIds = viewRankingStore.getTopIds(MAIN_PAGE_LIMIT);

        if (topIds.isEmpty()) {
            List<Long> fallbackIds = jobRepository.findTopByOrderByCreatedAtDesc(MAIN_PAGE_LIMIT)
                    .stream().map(JobPosting::getId).toList();
            mainPageCache.cacheIds(fallbackIds);
        } else {
            mainPageCache.cacheIds(topIds);
        }

        viewRankingStore.reset();
        log.info("메인 페이지 공고 {}건 갱신 완료", topIds.size());
    }

    /**
     * 메인 페이지 공고 목록 조회 (캐시 ID + 실시간 조회수)
     */
    public List<MainPagePostingReadModel> getMainPagePostings() {
        List<Long> cachedIds = mainPageCache.getCachedIds();

        if (cachedIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, JobPosting> postingMap = jobRepository.findByIdIn(cachedIds)
                .stream().collect(Collectors.toMap(JobPosting::getId, Function.identity()));

        return cachedIds.stream()
                .filter(postingMap::containsKey)
                .map(id -> {
                    JobPosting posting = postingMap.get(id);
                    int viewCount = viewCountStore.getCount(id);
                    return new MainPagePostingReadModel(id, posting.getTitle(), viewCount);
                })
                .toList();
    }
}
