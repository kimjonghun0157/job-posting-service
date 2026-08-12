package com.pickbit.jobpostingservice.domain.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<JobPosting, Long> {

    /**
     * 조회수 원자적 1 증가 (상한 100 미만일 때만 반영)
     */
    @Modifying
    @Query("UPDATE JobPosting j SET j.viewCount = j.viewCount + 1 WHERE j.id = :id AND j.viewCount < 100")
    void incrementViewCount(@Param("id") Long id);

    /**
     * ID 목록으로 공고 일괄 조회
     */
    List<JobPosting> findByIdIn(List<Long> ids);
}
