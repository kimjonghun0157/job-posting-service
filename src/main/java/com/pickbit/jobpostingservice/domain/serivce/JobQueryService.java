package com.pickbit.jobpostingservice.domain.serivce;

import com.pickbit.jobpostingservice.api.dto.CursorRequest;
import com.pickbit.jobpostingservice.api.dto.JobPostingListResponse;
import com.pickbit.jobpostingservice.api.dto.SliceResponse;
import com.pickbit.jobpostingservice.domain.repository.JobPostingQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobQueryService {

    private final JobPostingQueryRepository jobPostingQueryRepository;

    public SliceResponse<JobPostingListResponse> getJobPostings(CursorRequest request) {
        return SliceResponse.of(
                jobPostingQueryRepository.findByCursor(request),
                request.size(),
                JobPostingListResponse::id
        );
    }
}