package com.pickbit.jobpostingservice.domain.service;

import com.pickbit.jobpostingservice.domain.ViewPolicy;
import com.pickbit.jobpostingservice.domain.entity.ViewHistory;
import com.pickbit.jobpostingservice.domain.port.ViewMessageQueue;
import com.pickbit.jobpostingservice.domain.repository.JobRepository;
import com.pickbit.jobpostingservice.domain.repository.ViewHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 메시지 큐에서 조회 이력을 꺼내 DB에 일괄 반영
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewHistoryCommandService {

    private static final int BATCH_LIMIT = ViewPolicy.BATCH_FLUSH_SIZE;

    private final JobRepository jobRepository;
    private final ViewHistoryRepository viewHistoryRepository;
    private final ViewMessageQueue viewMessageQueue;

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
     * 큐에서 꺼내 job_posting.view_count 증가 + view_history insert
     */
    @Transactional
    public void flushViewQueue() {
        List<ViewMessage> batch = pollBatch();

        for (ViewMessage vm : batch) {
            try {

                int updated = jobRepository.incrementViewCount(vm.jobPostingId(), ViewPolicy.MAX_VIEW_COUNT);

                if (updated > 0) {
                    viewHistoryRepository.save(ViewHistory.create(
                            jobRepository.getReferenceById(vm.jobPostingId()),
                            vm.userId(),
                            vm.seqNumber()));
                }
            } catch (Exception e) {
                log.warn("조회 이력 처리 실패 [postingId={}, userId={}, seq={}]: {}",
                        vm.jobPostingId(), vm.userId(), vm.seqNumber(), e.getMessage());
            }
        }
    }

    /**
     * 큐에서 최대 BATCH_LIMIT건까지 꺼내 리스트로 반환
     */
    private List<ViewMessage> pollBatch() {
        List<ViewMessage> batch = new ArrayList<>();
        String message;
        while ((message = viewMessageQueue.poll()) != null) {
            batch.add(ViewMessage.parse(message));
            if (batch.size() >= BATCH_LIMIT) break;
        }
        return batch;
    }
}
