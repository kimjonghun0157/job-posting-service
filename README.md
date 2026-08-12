# 채용 공고 서비스 (Job Posting Service)

채용 공고를 자동 등록하고, 목록 조회·메인 페이지 노출·조회 이력을 관리합니다.

## 기술 스택

- **Java 25** / **Spring Boot 4.1.0**
- **PostgreSQL 17** — 메인 데이터 저장소
- **Redis 7** — 조회수 카운팅, 메인 페이지 캐시, 비동기 큐
- **QueryDSL 7.4.0** (OpenFeign fork) — 커서 기반 동적 쿼리
- **Testcontainers** — PostgreSQL + Redis 통합 테스트

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

Testcontainers가 PostgreSQL, Redis 컨테이너를 알아서 띄우므로 별도 인프라 없이 돌릴 수 있습니다.

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

| 파라미터 | 없음 |
|---------|------|

**응답** — 최대 100건

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

### 공고 자동 등록 — 배치 insert 최적화

5분마다 50건씩 공고를 자동 생성합니다. `saveAll()`은 건별 INSERT를 날리는데, **JPA SEQUENCE 전략** (`allocationSize=100`)으로 이 문제를 잡았습니다.

- 시퀀스를 100 단위로 선점하므로 50건 INSERT 시 ID 채번 쿼리가 1회만 발생
- `hibernate.jdbc.batch_size=100`, `order_inserts=true`로 batch INSERT 활성화
- 다른 엔티티(ViewHistory 등)도 같은 시퀀스를 공유하여 일관된 ID 채번

### 커서 기반 페이지네이션

offset 방식은 뒤쪽 페이지로 갈수록 느려지므로 **커서(ID) 기반**으로 구현했습니다.

- `WHERE id < :cursorId ORDER BY id DESC LIMIT :size+1`
- `size+1`건을 조회해서 초과분 존재 여부로 `hasNext` 판단 (COUNT 쿼리 불필요)
- QueryDSL로 cursorId 유무에 따른 동적 쿼리 구성
- `CursorSort`로 정렬 기준을 제한 — `id`, `createdAt`만 허용하고 그 외 필드는 예외 처리

### 메인 페이지 — Redis Sorted Set 랭킹

**선정 기준: 10분간 조회 급상승 공고 (트렌딩)**

"지금 관심이 몰리는 공고"를 메인에 띄웁니다. 조회수 상한이 100이라 단순 누적 조회수로는 의미 있는 순위가 나오기 어렵고, 채용 사이트 특성상 실시간 트렌딩이 더 맞다고 봤습니다.

한계점: 봇이나 반복 조회 공격에 대한 필터링이 없어 집계가 왜곡될 수 있습니다.

**동작 방식:**

1. 조회 이력 등록 시 Redis Sorted Set에 `ZINCRBY`로 공고별 점수 +1
2. 10분 주기 스케줄러가 `ZREVRANGE`로 상위 100건 ID를 Redis List에 캐시
3. 랭킹 Sorted Set을 초기화하여 다음 주기에 새로 집계
4. API 호출 시 캐시된 ID로 DB에서 제목 조회 + Redis에서 **실시간 조회수** 반영

**조회수가 실시간인 이유:**

목록 자체는 10분마다 바뀌지만, 각 공고의 조회수는 매 API 호출 시 `Redis GET view:count:{id}`로 읽어서 **즉시 반영**됩니다.

**고성능 설계:**

- 메인 페이지 API는 Redis List에서 ID를 읽고 DB에서 제목만 가져오니 응답이 빠릅니다
- 조회수는 Redis에서 바로 읽어 DB 부하가 없고, 동시 접속이 몰려도 Redis 단일 스레드 특성 덕에 병목이 생기지 않습니다

**Fallback:**

조회 이력이 없는 초기 상태에서는 최신 등록순으로 100건을 노출합니다.

### 조회 이력 등록 — Redis INCR 원자성 + DB 제약

**핵심 문제: 1,000명 동시 접속 시 정확히 100건만 기록**

**해결: Redis INCR 원자적 카운터 + DB 이중 안전장치**

```
[클라이언트] → [Redis INCR] → 100 이하? → [Redis 큐 적재] → [1초 주기 flush] → [DB 반영]
                                  ↓ 초과
                              [DECR 복원 + false 반환]
```

1. **Redis INCR** — `view:count:{id}` 키를 원자적으로 +1, 값이 100 초과 시 DECR 복원 후 거부
2. **Redis 큐** — 허용된 요청만 `view:queue`에 `{postingId}:{userId}:{seqNumber}` 형태로 적재
3. **DB flush** — 1초 주기 스케줄러가 큐에서 최대 500건씩 꺼내 DB에 반영
   - `job_posting.view_count += 1` (JPQL UPDATE, `WHERE view_count < 100`)
   - `view_history` INSERT (`seqNumber` = Redis INCR 반환값)
4. **DB 제약조건** — 최종 안전장치
   - `CHECK (seq_number BETWEEN 1 AND 100)` — 범위 강제
   - `UNIQUE (job_posting_id, seq_number)` — 중복 방지

**목표 반영 시간: ~1초**

Redis INCR은 즉시 반영되니 메인 페이지/목록 API에서 조회수가 바로 올라갑니다. DB 이력은 1초 주기 flush로 뒤따라 반영됩니다.

**동시성 검증:**

`ViewHistoryCommandServiceTest.concurrentAccessGuaranteesExactly100()` — `ExecutorService`로 1,000개 스레드를 동시에 쏘고, 최종 `viewCount == 100`, `historyCount == 100`인지 확인합니다.

---

## 아키텍처

### 설계 원칙

- **Port/Adapter (헥사고날)**: 도메인이 Redis 같은 인프라 구현을 모르게 포트 인터페이스를 두고, 인프라 레이어에서 구현
- **SRP**: `ViewCountRedisService` 하나에 몰려 있던 4가지 책임(카운팅/큐/랭킹/캐시)을 포트 4개 + 구현체 4개 + 오케스트레이션 서비스 1개로 분해
- **스케줄링 분리**: 서비스에서 `@Scheduled` 떼고 전용 스케줄러 클래스에 위임
- **레이어 경계**: 도메인 서비스는 읽기 모델(`ReadModel`)만 반환하고, 컨트롤러가 API DTO로 변환
- **도메인 행위**: 엔티티에 `canAcceptMoreViews()`, `ViewHistory.create()` 같은 비즈니스 로직을 넣어 빈약한 도메인 방지
- **매직 넘버 제거**: `ViewPolicy`에 `MAX_VIEW_COUNT=100` 등 정책 상수를 모아 한 곳에서 관리

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
│   │                  MainPageCache
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
                       RedisMainPageCache
```

---

## 현재 구조의 한계 및 확장 고려

현재 **단일 인스턴스**를 전제로 설계했습니다.

### 스케일 아웃 시 고려사항

| 영역 | 현재 | 확장 시 |
|------|------|---------|
| 공고 자동 등록 스케줄러 | `@Scheduled` (인스턴스별 독립 실행) | 다중 인스턴스에서 중복 실행 → **ShedLock** 또는 **분산 락(Redisson)**으로 단일 실행 보장 |
| 메인 페이지 갱신 스케줄러 | `@Scheduled` (인스턴스별 독립 실행) | 동일 이슈 → 분산 락 적용 필요 |
| Redis 큐 flush | 1초 주기 LPOP | 다중 인스턴스가 동시 LPOP해도 Redis 원자성으로 데이터 유실 없음 (안전) |
| 조회수 카운팅 | Redis INCR (원자적) | Redis 단일 노드 기준 안전. Redis Cluster 시에도 같은 키는 같은 샤드에서 처리되므로 안전 |

### 추가 개선 방향

- **Redis 큐 → Kafka/RabbitMQ**: 트래픽이 커지면 메시지 유실 방지와 재처리가 필요
- **메인 페이지 캐시 → Local Cache (Caffeine)**: Redis 호출 자체를 줄여 응답을 더 빠르게
- **DB `ddl-auto: create-drop` → Flyway 마이그레이션**: 운영 환경 대응
