package com.pickbit.jobpostingservice.api.controller;

import com.pickbit.jobpostingservice.api.dto.JobPostingListResponse;
import com.pickbit.jobpostingservice.api.dto.MainJobPostingResponse;
import com.pickbit.jobpostingservice.common.dto.CursorRequest;
import com.pickbit.jobpostingservice.common.dto.SliceResponse;
import com.pickbit.jobpostingservice.domain.dto.JobPostingReadModel;
import com.pickbit.jobpostingservice.domain.service.JobQueryService;
import com.pickbit.jobpostingservice.domain.service.MainPageService;
import com.pickbit.jobpostingservice.domain.service.ViewRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 채용 공고 API 컨트롤러 — 목록 조회, 메인 페이지, 조회 이력 등록 엔드포인트 제공
 */
@RestController
@RequiredArgsConstructor
public class JobPostingController {

    private final JobQueryService jobQueryService;
    private final ViewRegistrationService viewRegistrationService;
    private final MainPageService mainPageService;

    /**
     * 커서 기반 공고 목록 조회
     */
    @GetMapping("/api/job-postings")
    public SliceResponse<JobPostingListResponse> getJobPostings(@ModelAttribute CursorRequest request) {
        SliceResponse<JobPostingReadModel> slice = jobQueryService.getJobPostings(request);
        List<JobPostingListResponse> mapped = slice.content().stream()
                .map(r -> new JobPostingListResponse(r.id(), r.title(), r.company(), r.viewCount(), r.createdAt()))
                .toList();
        return new SliceResponse<>(mapped, slice.hasNext(), slice.lastId());
    }

    /**
     * 메인 페이지 공고 목록 조회 (10분 주기 갱신, 조회수 실시간)
     */
    @GetMapping("/api/job-postings/main")
    public List<MainJobPostingResponse> getMainPagePostings() {
        return mainPageService.getMainPagePostings().stream()
                .map(r -> new MainJobPostingResponse(r.id(), r.title(), r.viewCount()))
                .toList();
    }

    /**
     * 공고 조회 이력 등록 (큐 적재 → 비동기 DB 반영)
     */
    @PostMapping("/api/job-postings/{id}/view")
    public ResponseEntity<Void> registerView(@PathVariable Long id, @RequestParam Long userId) {
        boolean registered = viewRegistrationService.registerView(id, userId);
        return registered ? ResponseEntity.ok().build() : ResponseEntity.noContent().build();
    }
}
