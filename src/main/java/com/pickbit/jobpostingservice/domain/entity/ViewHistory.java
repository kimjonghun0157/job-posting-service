package com.pickbit.jobpostingservice.domain.entity;

import com.pickbit.jobpostingservice.domain.ViewPolicy;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 공고 조회 이력 엔티티 — 공고당 최대 100건, seqNumber(1~100) DB 제약 관리
 */
@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Table
public class ViewHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false, comment = "공고 ID")
    private JobPosting jobPosting;

    @Column(nullable = false, comment = "사용자 ID")
    private Long userId;

    @Column(nullable = false, comment = "이력 순번 (1~100)")
    private int seqNumber;

    /**
     * 조회 이력 생성 — seqNumber 범위(1~MAX_VIEW_COUNT) 검증 포함
     */
    public static ViewHistory create(JobPosting jobPosting, Long userId, int seqNumber) {
        if (seqNumber < 1 || seqNumber > ViewPolicy.MAX_VIEW_COUNT) {
            throw new IllegalArgumentException("seqNumber 범위 초과: " + seqNumber);
        }
        return ViewHistory.builder()
                .jobPosting(jobPosting)
                .userId(userId)
                .seqNumber(seqNumber)
                .build();
    }
}