package com.pickbit.jobpostingservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * 채용 공고 엔티티 — 제목, 기업명, 상세 내용, 조회수 관리
 */
@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPosting extends BaseEntity {

    @Column(nullable = false, length = 200, comment = "공고 제목")
    private String title;

    @Column(nullable = false, length = 200, comment = "기업명")
    private String company;

    @Column(nullable = false, columnDefinition = "TEXT", comment = "공고 상세 내용")
    private String description;

    @Column(nullable = false, comment = "조회수")
    @Builder.Default
    private int viewCount = 0;
}