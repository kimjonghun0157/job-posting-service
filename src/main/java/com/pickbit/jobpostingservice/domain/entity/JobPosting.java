package com.pickbit.jobpostingservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPosting extends BaseEntity {

    @Column(nullable = false, length = 200, comment = "공고 제목")
    private String title;

    @Column(nullable = false, length = 200, comment = "기업명")
    private String company;

    @Column(nullable = false, columnDefinition = "LONGTEXT", comment = "공고 상세 내용")
    private String description;

    @Column(nullable = false, comment = "조회수")
    private int viewCount=0;
}