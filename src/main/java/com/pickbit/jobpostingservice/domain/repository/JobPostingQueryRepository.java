package com.pickbit.jobpostingservice.domain.repository;

import com.pickbit.jobpostingservice.common.dto.CursorRequest;
import com.pickbit.jobpostingservice.common.dto.CursorSort;
import com.pickbit.jobpostingservice.domain.dto.JobPostingReadModel;
import com.pickbit.jobpostingservice.domain.entity.QJobPosting;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/**
 * QueryDSL 기반 공고 목록 커서 조회 리포지토리 — 허용된 정렬 필드만 사용 가능
 */
@Repository
@RequiredArgsConstructor
public class JobPostingQueryRepository {

    private final JPAQueryFactory queryFactory;
    private final QJobPosting jobPosting = QJobPosting.jobPosting;

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "createdAt");

    public List<JobPostingReadModel> findByCursor(CursorRequest request) {
        CursorSort sort = request.toCursorSort();
        validateSortField(sort.field());

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

    private void validateSortField(String field) {
        if (!SORTABLE_FIELDS.contains(field)) {
            throw new IllegalArgumentException("지원하지 않는 정렬 필드: " + field);
        }
    }

    private OrderSpecifier<?>[] resolveOrder(CursorSort sort) {
        ComparableExpressionBase<?> path = switch (sort.field()) {
            case "createdAt" -> jobPosting.createdAt;
            default -> jobPosting.id;
        };

        OrderSpecifier<?> primary = sort.direction() == CursorSort.Direction.ASC
                ? path.asc() : path.desc();

        if (!"id".equals(sort.field())) {
            OrderSpecifier<?> tiebreaker = sort.direction() == CursorSort.Direction.ASC
                    ? jobPosting.id.asc() : jobPosting.id.desc();
            return new OrderSpecifier<?>[]{ primary, tiebreaker };
        }

        return new OrderSpecifier<?>[]{ primary };
    }
}
