package com.pickbit.jobpostingservice.domain.service;

import com.pickbit.jobpostingservice.domain.ViewPolicy;
import com.pickbit.jobpostingservice.domain.port.UniqueViewStore;
import com.pickbit.jobpostingservice.domain.port.ViewCountStore;
import com.pickbit.jobpostingservice.domain.port.ViewMessageQueue;
import com.pickbit.jobpostingservice.domain.port.ViewRankingStore;
import com.pickbit.jobpostingservice.domain.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ViewRegistrationService {

    private final ViewCountStore viewCountStore;
    private final ViewMessageQueue viewMessageQueue;
    private final ViewRankingStore viewRankingStore;
    private final UniqueViewStore uniqueViewStore;
    private final JobRepository jobRepository;

    public boolean registerView(Long jobPostingId, Long userId) {
        if (!jobRepository.existsById(jobPostingId)) {
            throw new IllegalArgumentException("존재하지 않는 공고: " + jobPostingId);
        }
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
