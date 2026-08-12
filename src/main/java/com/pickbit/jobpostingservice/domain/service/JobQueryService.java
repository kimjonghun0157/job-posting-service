package com.pickbit.jobpostingservice.domain.service;

import com.pickbit.jobpostingservice.common.dto.CursorRequest;
import com.pickbit.jobpostingservice.common.dto.SliceResponse;
import com.pickbit.jobpostingservice.domain.dto.JobPostingReadModel;
import com.pickbit.jobpostingservice.domain.repository.JobPostingQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공고 목록 조회 서비스 — 커서 기반 페이지네이션 처리
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobQueryService {

    private final JobPostingQueryRepository jobPostingQueryRepository;

    public SliceResponse<JobPostingReadModel> getJobPostings(CursorRequest request) {
        return SliceResponse.of(
                jobPostingQueryRepository.findByCursor(request),
                request.size(),
                JobPostingReadModel::id
        );
    }
}
