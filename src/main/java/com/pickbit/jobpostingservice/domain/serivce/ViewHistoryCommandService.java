package com.pickbit.jobpostingservice.domain.serivce;

import com.pickbit.jobpostingservice.domain.entity.JobRepository;
import com.pickbit.jobpostingservice.domain.entity.ViewHistory;
import com.pickbit.jobpostingservice.domain.entity.ViewHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis 큐에서 조회 이력을 꺼내 DB에 일괄 반영
 */
@Service
@RequiredArgsConstructor
public class ViewHistoryCommandService {

    private static final int BATCH_LIMIT = 500;

    private final JobRepository jobRepository;
    private final ViewHistoryRepository viewHistoryRepository;
    private final ViewCountRedisService viewCountRedisService;

    /**
     * 큐 메시지 파싱용 레코드 (jobPostingId:userId:seqNumber)
     */
    private record ViewMessage(Long jobPostingId, Long userId, int seqNumber) {
        static ViewMessage parse(String message) {
            String[] parts = message.split(":");
            return new ViewMessage(
                    Long.parseLong(parts[0]),
                    Long.parseLong(parts[1]),
                    Integer.parseInt(parts[2])
            );
        }
    }

    /**
     * Redis 큐에서 꺼내 job_posting.view_count 증가 + view_history insert (1초 주기)
     */
    @Scheduled(fixedRate = 1000)
    @Transactional
    public void flushViewQueue() {
        List<ViewMessage> batch = pollBatch();

        for (ViewMessage vm : batch) {
            jobRepository.incrementViewCount(vm.jobPostingId());
            viewHistoryRepository.save(ViewHistory.builder()
                    .jobPosting(jobRepository.getReferenceById(vm.jobPostingId()))
                    .userId(vm.userId())
                    .seqNumber(vm.seqNumber())
                    .build());
        }
    }

    /**
     * Redis 큐에서 최대 BATCH_LIMIT건까지 꺼내 리스트로 반환
     */
    private List<ViewMessage> pollBatch() {
        List<ViewMessage> batch = new ArrayList<>();
        String message;
        while ((message = viewCountRedisService.pollQueue()) != null) {
            batch.add(ViewMessage.parse(message));
            if (batch.size() >= BATCH_LIMIT) break;
        }
        return batch;
    }
}