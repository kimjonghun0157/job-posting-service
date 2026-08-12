package com.pickbit.jobpostingservice.domain.service;

import com.pickbit.jobpostingservice.TestSupport;
import com.pickbit.jobpostingservice.domain.entity.JobPosting;
import com.pickbit.jobpostingservice.domain.entity.JobRepository;
import com.pickbit.jobpostingservice.domain.entity.ViewHistoryRepository;
import com.pickbit.jobpostingservice.domain.serivce.ViewCountRedisService;
import com.pickbit.jobpostingservice.domain.serivce.ViewHistoryCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "scheduler.job-posting.fixed-rate=999999999")
class ViewHistoryCommandServiceTest extends TestSupport {

    @Autowired
    private ViewCountRedisService viewCountRedisService;

    @Autowired
    private ViewHistoryCommandService viewHistoryCommandService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ViewHistoryRepository viewHistoryRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private JobPosting savedPosting;

    @BeforeEach
    void setUp() {
        viewHistoryRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
        savedPosting = jobRepository.save(JobPosting.builder()
                .title("테스트 공고")
                .company("테스트 기업")
                .description("테스트 상세 내용")
                .build());
    }

    /**
     * Redis 카운트 증가 후 flush하면 job_posting.view_count 1 증가, view_history 1건 insert
     */
    @Test
    @DisplayName("공고를 조회하면 Redis에 카운트되고 flush 후 DB에 반영된다")
    void registerViewIncrementsRedisAndFlushesToDb() {
        // given
        Long postingId = savedPosting.getId();

        // when
        boolean result = viewCountRedisService.registerView(postingId, 1L);
        viewHistoryCommandService.flushViewQueue();

        // then
        assertThat(result).isTrue();
        JobPosting posting = jobRepository.findById(postingId).orElseThrow();
        assertThat(posting.getViewCount()).isEqualTo(1);
        assertThat(viewHistoryRepository.countByJobPosting(posting)).isEqualTo(1);
    }

    /**
     * Redis INCR로 100회까지만 허용, 초과 시 false 반환
     */
    @Test
    @DisplayName("조회수가 100에 도달하면 더 이상 기록되지 않는다")
    void registerViewStopsAt100() {
        // given
        Long postingId = savedPosting.getId();
        for (int i = 0; i < 100; i++) {
            viewCountRedisService.registerView(postingId, (long) i);
        }

        // when
        boolean result = viewCountRedisService.registerView(postingId, 999L);
        flushAll();

        // then
        assertThat(result).isFalse();
        JobPosting posting = jobRepository.findById(postingId).orElseThrow();
        assertThat(posting.getViewCount()).isEqualTo(100);
        assertThat(viewHistoryRepository.countByJobPosting(posting)).isEqualTo(100);
    }

    /**
     * 1000개 스레드 동시 호출 시 Redis INCR 원자성으로 정확히 100건만 허용
     */
    @Test
    @DisplayName("1000명이 동시에 조회해도 조회수와 이력은 정확히 100건이다")
    void concurrentAccessGuaranteesExactly100() throws InterruptedException {
        // given
        Long postingId = savedPosting.getId();
        int threadCount = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            long userId = i;
            executor.submit(() -> {
                try {
                    if (viewCountRedisService.registerView(postingId, userId)) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();
        flushAll();

        // then
        assertThat(successCount.get()).isEqualTo(100);
        JobPosting posting = jobRepository.findById(postingId).orElseThrow();
        assertThat(posting.getViewCount()).isEqualTo(100);
        assertThat(viewHistoryRepository.countByJobPosting(posting)).isEqualTo(100);
    }

    private void flushAll() {
        Long queueSize;
        do {
            viewHistoryCommandService.flushViewQueue();
            queueSize = viewCountRedisService.getQueueSize();
        } while (queueSize != null && queueSize > 0);
    }
}