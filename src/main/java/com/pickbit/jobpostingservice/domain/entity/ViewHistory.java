package com.pickbit.jobpostingservice.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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
}