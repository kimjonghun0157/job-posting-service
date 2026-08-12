package com.pickbit.jobpostingservice.domain.repository;

import com.pickbit.jobpostingservice.domain.entity.JobPosting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 채용 공고 JPA 리포지토리 — 조회수 원자적 증가, ID 목록 조회, 최신순 조회
 */
@Repository
public interface JobRepository extends JpaRepository<JobPosting, Long> {

    /**
     * 조회수 원자적 1 증가 (상한 미만일 때만 반영)
     */
    @Modifying
    @Query("UPDATE JobPosting j SET j.viewCount = j.viewCount + 1 WHERE j.id = :id AND j.viewCount < :maxCount")
    int incrementViewCount(@Param("id") Long id, @Param("maxCount") int maxCount);

    /**
     * ID 목록으로 공고 일괄 조회
     */
    List<JobPosting> findByIdIn(List<Long> ids);

    /**
     * 최신 등록순 공고 N건 조회 (메인 페이지 fallback용)
     */
    @Query("SELECT j FROM JobPosting j ORDER BY j.createdAt DESC LIMIT :limit")
    List<JobPosting> findTopByOrderByCreatedAtDesc(@Param("limit") int limit);
}
