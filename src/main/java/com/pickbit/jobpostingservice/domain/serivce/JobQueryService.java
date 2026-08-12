package com.pickbit.jobpostingservice.domain.serivce;

import com.pickbit.jobpostingservice.api.dto.JobPostingSliceResponse;
import com.pickbit.jobpostingservice.api.mapper.JobPostingMapper;
import com.pickbit.jobpostingservice.domain.repository.JobPostingQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobQueryService {

    private final JobPostingQueryRepository jobPostingQueryRepository;
    private final JobPostingMapper jobPostingMapper;

    public JobPostingSliceResponse getJobPostings(Long cursorId, Pageable pageable) {
        return jobPostingMapper.toSliceResponse(
                jobPostingQueryRepository.findByCursor(cursorId, pageable));
    }
}