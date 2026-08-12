package com.pickbit.jobpostingservice.domain.repository;

import com.pickbit.jobpostingservice.common.dto.CursorRequest;
import com.pickbit.jobpostingservice.common.dto.CursorSort;
import com.pickbit.jobpostingservice.domain.dto.JobPostingReadModel;
import com.pickbit.jobpostingservice.domain.entity.QJobPosting;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * QueryDSL 기반 공고 목록 커서 조회 리포지토리 — 허용된 정렬 필드만 사용 가능
 */
@Repository
@RequiredArgsConstructor
public class JobPostingQueryRepository {

    private final JPAQueryFactory queryFactory;
    private final QJobPosting jobPosting = QJobPosting.jobPosting;

    public List<JobPostingReadModel> findByCursor(CursorRequest request) {
        CursorSort sort = request.toCursorSort();

        var query = queryFactory
                .select(Projections.constructor(JobPostingReadModel.class,
                        jobPosting.id,
                        jobPosting.title,
                        jobPosting.company,
                        jobPosting.viewCount,
                        jobPosting.createdAt
                ))
                .from(jobPosting)
                .orderBy(resolveOrder(sort));

        if (request.cursorId() != null) {
            if (sort.direction() == CursorSort.Direction.ASC) {
                query.where(jobPosting.id.gt(request.cursorId()));
            } else {
                query.where(jobPosting.id.lt(request.cursorId()));
            }
        }

        return query
                .limit(request.size() + 1)
                .fetch();
    }

    private OrderSpecifier<?>[] resolveOrder(CursorSort sort) {
        return sort.direction() == CursorSort.Direction.ASC
                ? new OrderSpecifier<?>[]{ jobPosting.id.asc() }
                : new OrderSpecifier<?>[]{ jobPosting.id.desc() };
    }
}
