package com.pickbit.jobpostingservice.domain.scheduler;

import com.pickbit.jobpostingservice.domain.entity.JobPosting;
import com.pickbit.jobpostingservice.domain.entity.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobPostingScheduler {

    private static final String[] TITLES = {
            "백엔드 개발자", "프론트엔드 개발자", "풀스택 개발자", "데이터 엔지니어",
            "DevOps 엔지니어", "iOS 개발자", "Android 개발자", "QA 엔지니어",
            "ML 엔지니어", "보안 엔지니어", "클라우드 아키텍트", "DBA",
            "임베디드 개발자", "게임 서버 개발자", "SRE 엔지니어"
    };

    private static final String[] COMPANIES = {
            "네이버", "카카오", "라인", "쿠팡", "배달의민족",
            "토스", "당근마켓", "왓챠", "리디", "마켓컬리",
            "야놀자", "직방", "버킷플레이스", "센드버드", "채널톡"
    };

    private static final String[] LEVELS = {"주니어", "미들", "시니어", "리드"};

    private static final String[] DESCRIPTIONS = {
            "저희 팀과 함께 대규모 트래픽을 처리하는 서비스를 개발할 인재를 찾습니다.",
            "빠르게 성장하는 스타트업에서 핵심 서비스를 함께 만들어갈 분을 모십니다.",
            "글로벌 서비스 확장을 위해 새로운 팀원을 모집합니다.",
            "사용자 경험을 혁신할 창의적인 개발자를 기다립니다.",
            "안정적인 인프라 환경에서 도전적인 문제를 풀어갈 분을 찾습니다."
    };

    private final JobRepository jobRepository;

    @Scheduled(fixedRate = 100_000)
    @Transactional
    public void autoRegister() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<JobPosting> postings = new ArrayList<>(50);

        for (int i = 0; i < 50; i++) {
            String title = LEVELS[random.nextInt(LEVELS.length)] + " "
                    + TITLES[random.nextInt(TITLES.length)] + " 채용";
            String company = COMPANIES[random.nextInt(COMPANIES.length)];
            String description = DESCRIPTIONS[random.nextInt(DESCRIPTIONS.length)];

            postings.add(JobPosting.builder()
                    .title(title)
                    .company(company)
                    .description(description)
                    .build());
        }

        jobRepository.saveAll(postings);
        log.info("공고 50건 자동 등록 완료");
    }
}