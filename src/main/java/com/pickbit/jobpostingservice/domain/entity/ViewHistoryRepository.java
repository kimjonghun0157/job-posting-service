package com.pickbit.jobpostingservice.domain.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ViewHistoryRepository extends JpaRepository<ViewHistory, Long> {

    /**
     * 특정 공고의 조회 이력 건수 조회
     */
    long countByJobPosting(JobPosting jobPosting);

    /**
     * 특정 공고의 최대 이력 순번 조회 (없으면 0)
     */
    @Query("SELECT COALESCE(MAX(v.seqNumber), 0) FROM ViewHistory v WHERE v.jobPosting.id = :jobPostingId")
    int findMaxSeqNumber(@Param("jobPostingId") Long jobPostingId);
}