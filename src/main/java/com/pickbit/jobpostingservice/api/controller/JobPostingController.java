package com.pickbit.jobpostingservice.api.controller;

import com.pickbit.jobpostingservice.api.dto.CursorRequest;
import com.pickbit.jobpostingservice.api.dto.JobPostingListResponse;
import com.pickbit.jobpostingservice.api.dto.SliceResponse;
import com.pickbit.jobpostingservice.domain.serivce.JobQueryService;
import com.pickbit.jobpostingservice.domain.serivce.ViewCountRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class JobPostingController {

    private final JobQueryService jobQueryService;
    private final ViewCountRedisService viewCountRedisService;

    /**
     * 커서 기반 공고 목록 조회
     */
    @GetMapping("/api/job-postings")
    public SliceResponse<JobPostingListResponse> getJobPostings(@ModelAttribute CursorRequest request) {
        return jobQueryService.getJobPostings(request);
    }

    /**
     * 공고 조회 이력 등록 (Redis INCR → 큐 적재 → 비동기 DB 반영)
     */
    @PostMapping("/api/job-postings/{id}/view")
    public ResponseEntity<Void> registerView(@PathVariable Long id, @RequestParam Long userId) {
        boolean registered = viewCountRedisService.registerView(id, userId);
        return registered ? ResponseEntity.ok().build() : ResponseEntity.noContent().build();
    }
}
