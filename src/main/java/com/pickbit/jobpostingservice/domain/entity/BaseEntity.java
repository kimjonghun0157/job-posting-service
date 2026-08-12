package com.pickbit.jobpostingservice.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 공통 엔티티 — ID(SEQUENCE, allocationSize=100), 등록 일시, 수정 일시 자동 관리
 */
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq")
    @SequenceGenerator(name = "seq", sequenceName = "seq", allocationSize = 100)
    @Column(comment = "ID")
    private Long id;

    @CreatedDate
    @Column(nullable = false, updatable = false, comment = "등록 일시")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false, comment = "수정 일시")
    private LocalDateTime updatedAt;
}