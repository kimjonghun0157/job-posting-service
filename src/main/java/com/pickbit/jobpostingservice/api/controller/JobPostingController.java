package com.pickbit.jobpostingservice.api.controller;

import com.pickbit.jobpostingservice.api.dto.CursorRequest;
import com.pickbit.jobpostingservice.api.dto.JobPostingListResponse;
import com.pickbit.jobpostingservice.api.dto.SliceResponse;
import com.pickbit.jobpostingservice.domain.serivce.JobQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class JobPostingController {

    private final JobQueryService jobQueryService;

    @GetMapping("/api/job-postings")
    public SliceResponse<JobPostingListResponse> getJobPostings(@ModelAttribute CursorRequest request) {
        return jobQueryService.getJobPostings(request);
    }
}
