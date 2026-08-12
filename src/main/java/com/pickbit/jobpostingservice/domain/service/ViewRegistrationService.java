package com.pickbit.jobpostingservice.domain.service;

import com.pickbit.jobpostingservice.domain.ViewPolicy;
import com.pickbit.jobpostingservice.domain.port.UniqueViewStore;
import com.pickbit.jobpostingservice.domain.port.ViewCountStore;
import com.pickbit.jobpostingservice.domain.port.ViewMessageQueue;
import com.pickbit.jobpostingservice.domain.port.ViewRankingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 조회 등록 도메인 서비스 — INCR → 상한 체크 → 큐 적재 → 고유 조회 시에만 랭킹 가산
 */
@Service
@RequiredArgsConstructor
public class ViewRegistrationService {

    private final ViewCountStore viewCountStore;
    private final ViewMessageQueue viewMessageQueue;
    private final ViewRankingStore viewRankingStore;
    private final UniqueViewStore uniqueViewStore;

    /**
     * 조회 등록 — 상한 이내면 큐 적재, 해당 유저의 첫 조회일 때만 랭킹 반영
     */
    public boolean registerView(Long jobPostingId, Long userId) {
        Long count = viewCountStore.increment(jobPostingId);

        if (count == null || count > ViewPolicy.MAX_VIEW_COUNT) {
            if (count != null) {
                viewCountStore.decrement(jobPostingId);
            }
            return false;
        }

        String message = jobPostingId + ":" + userId + ":" + count;
        viewMessageQueue.push(message);

        if (uniqueViewStore.isFirstView(jobPostingId, userId)) {
            viewRankingStore.incrementScore(jobPostingId);
        }

        return true;
    }
}
