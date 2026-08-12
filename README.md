# 채용 공고 서비스 (Job Posting Service)

채용 공고를 자동 등록하고, 목록 조회·메인 페이지 노출·조회 이력을 관리하는 서비스입니다.

## 기술 스택

- **Java 25** / **Spring Boot 4.1.0**
- **PostgreSQL 17** — 메인 데이터 저장소
- **Redis 7** — 조회수 카운팅, 메인 페이지 캐시, 비동기 큐, 랭킹
- **QueryDSL 7.4.0** (OpenFeign fork) — 커서 기반 동적 쿼리
- **Testcontainers** — H2 대신 실제 PostgreSQL·Redis로 테스트 (CHECK 제약조건, Redis 명령어 등 실DB 동작 검증)

## 실행 방법

### 1. 인프라 실행

```bash
cd docker
docker-compose up -d
```

PostgreSQL 17 (port 5432)과 Redis 7 (port 6379)이 실행됩니다.

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

### 3. 테스트 실행

```bash
./gradlew clean test
```

Testcontainers가 PostgreSQL·Redis 컨테이너를 자동으로 띄우므로 별도 인프라 없이 실행할 수 있습니다.

---

## API 명세

### 1. 공고 목록 조회 (커서 기반 페이지네이션)

```
GET /api/job-postings?cursorId={lastId}&size={size}&sortBy={field}&direction={ASC|DESC}
```

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| cursorId | Long | N | 마지막으로 받은 공고 ID (없으면 최신부터) |
| size | int | N | 조회 건수 (기본값 20) |
| sortBy | String | N | 정렬 필드 — `id`, `createdAt` (기본값 `id`) |
| direction | String | N | 정렬 방향 — `ASC`, `DESC` (기본값 `DESC`) |

**응답**

```json
{
  "content": [
    {
      "id": 150,
      "title": "시니어 백엔드 개발자 채용",
      "company": "토스",
      "viewCount": 42,
      "createdAt": "2026-08-12T10:30:00"
    }
  ],
  "hasNext": true,
  "lastId": 141
}
```

### 2. 메인 페이지 공고 목록 조회

```
GET /api/job-postings/main
```

조회 이력 기반 트렌딩 상위 최대 100건을 반환합니다.

**응답**

```json
[
  {
    "id": 42,
    "title": "시니어 백엔드 개발자 채용",
    "viewCount": 87
  }
]
```

### 3. 공고 조회 이력 등록

```
POST /api/job-postings/{id}/view?userId={userId}
```

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| id | Long | Y | 공고 ID (Path) |
| userId | Long | Y | 사용자 ID (Query) |

| 응답 코드 | 의미 |
|----------|------|
| 200 OK | 조회수 증가 + 이력 등록 성공 |
| 204 No Content | 상한(100회) 도달로 기록하지 않음 |

---

## 기술적 설계

### 공고 자동 등록 — 배치 INSERT 최적화

5분마다 50건씩 공고를 자동 생성합니다.

- JPA SEQUENCE 전략(`allocationSize=100`)으로 50건 INSERT 시 ID 채번 쿼리 1회
- `hibernate.jdbc.batch_size=100`, `order_inserts=true`로 batch INSERT 활성화
- 다른 엔티티(ViewHistory 등)도 같은 시퀀스를 공유하여 일관된 ID 채번
- 서버 재시작 시 선점한 시퀀스 구간이 소멸되어 ID에 공백이 생길 수 있지만, batch INSERT 성능을 우선했습니다

### 커서 기반 페이지네이션

offset 방식은 뒤쪽 페이지로 갈수록 느려지므로 커서(ID) 기반으로 구현했습니다.

- `WHERE id < :cursorId ORDER BY id DESC LIMIT :size+1`
- `size+1`건을 조회해서 초과분 존재 여부로 `hasNext` 판단 (COUNT 쿼리 불필요)
- QueryDSL로 cursorId 유무에 따른 동적 쿼리 구성
- `CursorSort`로 정렬 기준을 `id`, `createdAt`으로 제한하고 그 외 필드는 예외 처리

### 메인 페이지 — Redis Sorted Set 랭킹

**선정 기준: 10분간 조회 급상승 공고 (트렌딩)**

"지금 관심이 몰리는 공고"를 메인에 띄웁니다. 조회수 상한이 100이라 단순 누적 조회수로는 의미 있는 순위가 나오기 어렵고, 채용 사이트 특성상 실시간 트렌딩이 적합하다고 판단했습니다.

Sorted Set은 `ZINCRBY`로 점수 갱신과 `ZREVRANGE`로 상위 N건 조회가 O(log N)이라 DB 집계 쿼리 없이 실시간 랭킹이 가능합니다.

**동작 방식:**

1. 조회 이력 등록 시 Redis Sorted Set에 `ZINCRBY`로 공고별 점수 +1
2. 10분 주기 스케줄러가 `ZREVRANGE`로 상위 100건 ID를 Redis List에 캐시
3. 랭킹 Sorted Set을 초기화하여 다음 주기에 새로 집계
4. API 호출 시 캐시된 ID로 DB에서 제목 조회 + Redis에서 실시간 조회수 반영

**랭킹 신뢰성 — UniqueViewStore:**

같은 사용자가 같은 공고를 반복 클릭해도 랭킹 점수는 1회만 반영됩니다. Redis Set(`SADD`)으로 `{공고ID, 유저ID}` 쌍의 중복을 원자적으로 판별하고, 첫 조회일 때만 랭킹 점수를 올립니다. TTL은 랭킹 갱신 주기(10분)보다 여유 있게 15분으로 설정했습니다.

**조회수는 실시간:**

목록 자체는 10분마다 갱신되지만, 각 공고의 조회수는 매 API 호출 시 Redis에서 직접 읽어 즉시 반영됩니다.

**한계점:** 같은 유저의 중복 조회는 UniqueViewStore로 막지만, 봇이 다수의 userId로 점수를 조작하는 경우는 필터링하지 못합니다.

**Fallback:**

조회 이력이 없는 초기 상태에서는 최신 등록순으로 100건을 노출합니다.

### 조회 이력 등록 — Redis INCR 원자성 + DB 제약

**핵심 문제: 1,000명 동시 접속 시 정확히 100건만 기록**

DB `SELECT FOR UPDATE`는 1,000명이 동시에 몰리면 락 경합으로 타임아웃 위험이 있습니다. Redis INCR은 단일 스레드에서 원자적으로 처리되므로 락 없이 동시성을 해결할 수 있고, 큐에 모아 1초 주기로 batch flush하면 DB 쓰기 부하도 평탄화됩니다.

```
[클라이언트] → [Redis INCR] → 100 이하? → [Redis 큐 적재] → [1초 주기 flush] → [DB 반영]
                                  ↓ 초과
                              [DECR 복원 + false 반환]
```

1. **Redis INCR** — `view:count:{id}` 키를 원자적으로 +1, 100 초과 시 DECR 복원 후 거부
2. **Redis 큐** — 허용된 요청만 `view:queue`에 `{postingId}:{userId}:{seqNumber}` 형태로 적재
3. **DB flush** — 1초 주기 스케줄러가 큐에서 최대 500건씩 꺼내 DB에 반영
   - `job_posting.view_count += 1` (JPQL UPDATE, `WHERE view_count < 100`)
   - `view_history` INSERT (`seqNumber` = Redis INCR 반환값)
4. **DB 제약조건** — 최종 안전장치
   - `CHECK (seq_number BETWEEN 1 AND 100)` — 범위 강제
   - `UNIQUE (job_posting_id, seq_number)` — 중복 방지

**트레이드오프:**

- 카운팅·큐·랭킹·캐시를 전부 Redis에 의존하므로 Redis 장애 시 서비스 전체가 영향받습니다. 단일 인프라로 운영 복잡도를 낮추는 대가입니다.
- Redis crash 시 큐에 적재됐지만 미처리된 이력이 유실될 수 있습니다. 조회수(INCR)는 살아 있지만 DB 이력과 불일치가 발생할 수 있고, 동기 쓰기 대비 처리량을 택한 결과입니다.

**목표 반영 시간: ~1초**

Redis INCR은 즉시 반영되므로 메인 페이지·목록 API에서 조회수가 바로 올라갑니다. DB 이력은 1초 주기 flush로 뒤따라 반영됩니다.

**동시성 검증:**

`ViewHistoryCommandServiceTest.concurrentAccessGuaranteesExactly100()` — `ExecutorService`로 1,000개 요청을 동시에 쏘고 최종 `viewCount == 100`, `historyCount == 100`을 확인합니다.

---

## 아키텍처

### 구조 결정

- 도메인 서비스가 Redis를 직접 쓰지 않도록 포트 인터페이스(`ViewCountStore`, `ViewMessageQueue` 등)를 두고 구현체는 `infra/redis`에 분리
- 도메인 서비스는 읽기 모델(`JobPostingReadModel` 등)을 반환하고, 컨트롤러에서 API 응답 DTO로 변환

### 프로젝트 구조

```
src/main/java/com/pickbit/jobpostingservice/
├── api/
│   ├── controller/    JobPostingController            — API 엔드포인트
│   └── dto/           JobPostingListResponse,         — API 응답 DTO
│                      MainJobPostingResponse
├── common/
│   └── dto/           CursorRequest, CursorSort,      — 공용 페이지네이션 타입
│                      SliceResponse
├── config/            QueryDslConfig, RedisConfig      — 설정
├── domain/
│   ├── ViewPolicy                                     — 도메인 정책 상수
│   ├── dto/           JobPostingReadModel,             — 도메인 읽기 모델
│   │                  MainPagePostingReadModel
│   ├── entity/        BaseEntity, JobPosting,          — JPA 엔티티
│   │                  ViewHistory
│   ├── port/          ViewCountStore,                  — 인프라 포트 인터페이스
│   │                  ViewMessageQueue,
│   │                  ViewRankingStore,
│   │                  MainPageCache,
│   │                  UniqueViewStore
│   ├── repository/    JobRepository,                   — 리포지토리
│   │                  ViewHistoryRepository,
│   │                  JobPostingQueryRepository
│   ├── scheduler/     JobPostingScheduler,             — 주기 작업 스케줄러
│   │                  ViewHistoryScheduler,
│   │                  MainPageScheduler
│   └── service/       JobPostingAutoCreateService,     — 비즈니스 서비스
│                      JobQueryService,
│                      MainPageService,
│                      ViewRegistrationService,
│                      ViewHistoryCommandService
└── infra/
    └── redis/         RedisViewCountStore,             — Redis 포트 구현체
                       RedisViewMessageQueue,
                       RedisViewRankingStore,
                       RedisMainPageCache,
                       RedisUniqueViewStore
```

---

## 부하 테스트

k6를 사용한 HTTP 레벨 부하 테스트입니다. 스크립트는 `load-test/` 디렉토리에 있습니다.

### 시나리오

| # | 시나리오 | VU | 시간 | 검증 포인트 |
|---|---------|-----|------|------------|
| 1 | 조회 이력 동시성 | 1,000 × 1회 | — | 200 OK = 100, 204 = 900 |
| 2 | 공고 목록 성능 | 50 | 30초 | p(95) < 200ms |
| 3 | 메인 페이지 고동시성 | 1,000 | 30초 | p(95) < 100ms |
| 4 | 혼합 워크로드 | 100 | 1분 | 메인 70% / 목록 20% / 조회 10%, p(95) < 500ms |
| 5 | 스트레스 | 500→5,000 | 2.5분 | 한계점 탐색 (에러율 < 5%) |
| 6 | DB 커넥션 풀 한계점 | 0→500 | 2.5분 | 목록 API 100%, no sleep, HikariCP 포화 지점 |
| 7 | 쓰기 포화 + 읽기 경합 | 500+200 | 2분 | 쓰기 500VU + 읽기 200VU 동시, 스케줄러 영향 측정 |
| 8 | 스파이크 | 0→5,000 즉시 | 1.5분 | 1초 만에 최대 부하, 점진적 증가와 비교 |

### 실행 방법

```bash
brew install k6                                                    # 최초 1회
./gradlew bootRun --args='--spring.profiles.active=stress'         # 앱 기동 (별도 터미널)
cd load-test && sh run-all.sh                                      # 부하 테스트 실행
```

`application-stress.yml`은 Tomcat 500스레드, HikariCP 30커넥션으로 튜닝한 프로파일입니다.

### 테스트 결과 (12코어 48GB Mac, 단일 인스턴스)

| # | 시나리오 | VU | 총 요청 | RPS | p(95) | 에러율 | 결과 |
|---|---------|-----|--------|------|-------|--------|------|
| 1 | 동시성 검증 | 1,000 | 1,000 | 4,329 | 46ms | 0% | **PASS** (200=100, 204=900 정확) |
| 2 | 공고 목록 성능 | 50 | 26,962 | 895 | 12ms | 0% | **PASS** |
| 3 | 메인 페이지 극한 동시성 | 1,000 | 290,746 | 9,659 | 7ms | 0% | **PASS** |
| 4 | 혼합 워크로드 | 100 | 57,715 | 960 | 9ms | 0% | **PASS** |
| 5 | 스트레스 (500→5,000VU) | 5,000 | 2,955,306 | 18,464 | 146ms | 0% | **PASS** |
| 6 | DB 커넥션 풀 한계점 | 0→500 | 1,723,434 | 10,771 | 84ms | 0% | **PASS** |
| 7 | 쓰기 포화 + 읽기 경합 | 500+200 | 2,240,290 | 18,660 | 쓰기 77ms / 읽기 57ms | 0% | **PASS** |
| 8 | 스파이크 (0→5,000 즉시) | 5,000 | 166,402 | 1,732 | 2,999ms | 0% | **PASS** |

**동시성 검증 (시나리오 1):** 요구사항 "1,000명 동시 접속 시 정확히 100"을 HTTP 레벨에서 재현했습니다. VU 1,000이 동시에 같은 공고에 조회 요청을 보내고, Redis INCR 원자성으로 200 OK 100건, 204 No Content 900건이 정확히 반환됩니다.

**스트레스 테스트 (시나리오 5):** VU를 500에서 5,000까지 단계적으로 올리며 혼합 워크로드를 2분 40초 동안 실행했습니다. VU 5,000에서도 에러 0%, p(95) 146ms로 단일 인스턴스 기준 여유가 있습니다.

**DB 커넥션 풀 한계점 (시나리오 6):** 목록 API만 sleep 없이 500VU까지 올렸습니다. HikariCP 30커넥션으로 RPS 10,771, p(95) 84ms — 커넥션 풀 포화에도 타임아웃 0건, 에러 0%로 안정적입니다.

**쓰기 포화 + 읽기 경합 (시나리오 7):** 쓰기 500VU와 읽기 200VU를 동시에 2분간 실행했습니다. 1초 주기 flush 스케줄러가 동작하는 중에도 읽기 p(95)=57ms로 영향이 미미합니다.

**스파이크 (시나리오 8):** 1초 만에 0→5,000VU로 즉시 부하를 걸었습니다. 점진적 증가(시나리오 5, p(95)=146ms) 대비 p(95)=2,999ms로 레이턴시가 급등하지만, connection refused 0건, 에러 0%로 요청 유실 없이 처리했습니다.

---

## 한계 및 확장 고려

현재 단일 인스턴스를 전제로 설계했습니다.

### 스케일 아웃 시 고려사항

| 영역 | 현재 | 확장 시 |
|------|------|---------|
| 공고 자동 등록 스케줄러 | `@Scheduled` (인스턴스별 독립 실행) | 다중 인스턴스에서 중복 실행 → ShedLock 또는 분산 락으로 단일 실행 보장 |
| 메인 페이지 갱신 스케줄러 | `@Scheduled` (인스턴스별 독립 실행) | 동일 이슈 → 분산 락 적용 필요 |
| Redis 큐 flush | 1초 주기 LPOP | 다중 인스턴스가 동시 LPOP해도 Redis 원자성으로 데이터 유실 없음 |
| 조회수 카운팅 | Redis INCR (원자적) | Redis Cluster 시에도 같은 키는 같은 샤드에서 처리되므로 안전 |

