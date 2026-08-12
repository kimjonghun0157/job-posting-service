package com.pickbit.jobpostingservice.domain.repository;

import com.pickbit.jobpostingservice.api.dto.CursorRequest;
import com.pickbit.jobpostingservice.api.dto.JobPostingListResponse;
import com.pickbit.jobpostingservice.domain.entity.QJobPosting;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class JobPostingQueryRepository {

    private final JPAQueryFactory queryFactory;
    private final QJobPosting jobPosting = QJobPosting.jobPosting;

    public List<JobPostingListResponse> findByCursor(CursorRequest request) {
        var query = queryFactory
                .select(Projections.constructor(JobPostingListResponse.class,
                        jobPosting.id,
                        jobPosting.title,
                        jobPosting.company,
                        jobPosting.viewCount,
                        jobPosting.createdAt
                ))
                .from(jobPosting)
                .orderBy(jobPosting.id.desc());

        if (request.cursorId() != null) {
            query.where(jobPosting.id.lt(request.cursorId()));
        }

        return query
                .limit(request.size() + 1)
                .fetch();
    }
}