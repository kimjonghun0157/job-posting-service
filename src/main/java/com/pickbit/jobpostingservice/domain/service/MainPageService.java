package com.pickbit.jobpostingservice.domain.service;

import com.pickbit.jobpostingservice.domain.ViewPolicy;
import com.pickbit.jobpostingservice.domain.dto.MainPageCacheEntry;
import com.pickbit.jobpostingservice.domain.dto.MainPagePostingReadModel;
import com.pickbit.jobpostingservice.domain.entity.JobPosting;
import com.pickbit.jobpostingservice.domain.port.MainPageCache;
import com.pickbit.jobpostingservice.domain.port.ViewCountStore;
import com.pickbit.jobpostingservice.domain.port.ViewRankingStore;
import com.pickbit.jobpostingservice.domain.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * 제목까지 함께 캐싱해 조회 경로에서 DB를 읽지 않도록 한다.
     */
    public void refreshMainPage() {
        List<Long> topIds = viewRankingStore.getTopIds(MAIN_PAGE_LIMIT);

        List<MainPageCacheEntry> entries = topIds.isEmpty()
                ? toEntries(jobRepository.findTopByOrderByCreatedAtDesc(MAIN_PAGE_LIMIT))
                : findInRankingOrder(topIds);

        mainPageCache.cache(entries);

        viewRankingStore.reset();
        log.info("메인 페이지 공고 {}건 갱신 완료", entries.size());
    }

    /**
     * 메인 페이지 공고 목록 조회 (캐시된 ID·제목 + 실시간 조회수)
     * 캐시 조회와 조회수 MGET 만으로 응답하며 DB에는 접근하지 않는다.
     */
    public List<MainPagePostingReadModel> getMainPagePostings() {
        List<MainPageCacheEntry> entries = mainPageCache.getCached();

        if (entries.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> ids = entries.stream().map(MainPageCacheEntry::id).toList();
        List<Integer> viewCounts = viewCountStore.getCounts(ids);

        List<MainPagePostingReadModel> result = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            MainPageCacheEntry entry = entries.get(i);
            result.add(new MainPagePostingReadModel(entry.id(), entry.title(), viewCounts.get(i)));
        }
        return result;
    }

    /**
     * 랭킹 순서를 유지한 채 제목을 채운다. DB에 없는 ID는 제외한다.
     */
    private List<MainPageCacheEntry> findInRankingOrder(List<Long> topIds) {
        Map<Long, JobPosting> postingMap = jobRepository.findByIdIn(topIds)
                .stream().collect(Collectors.toMap(JobPosting::getId, Function.identity()));

        return topIds.stream()
                .map(postingMap::get)
                .filter(Objects::nonNull)
                .map(this::toEntry)
                .toList();
    }

    private List<MainPageCacheEntry> toEntries(List<JobPosting> postings) {
        return postings.stream().map(this::toEntry).toList();
    }

    private MainPageCacheEntry toEntry(JobPosting posting) {
        return new MainPageCacheEntry(posting.getId(), posting.getTitle());
    }
}
