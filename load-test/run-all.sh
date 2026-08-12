#!/bin/bash
set +e

BASE_URL="${BASE_URL:-http://localhost:8080}"
echo "=== 부하 테스트 시작 (대상: $BASE_URL) ==="
echo ""
echo "권장: stress 프로파일로 앱을 기동하세요"
echo "  ./gradlew bootRun --args='--spring.profiles.active=stress'"
echo ""

# 앱 상태 확인
echo "[사전 확인] 서버 응답 확인..."
if ! curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/job-postings?size=1" | grep -q "200"; then
    echo "ERROR: 서버가 응답하지 않습니다. 앱을 먼저 기동하세요."
    exit 1
fi

# 공고 존재 확인
POSTING_COUNT=$(curl -s "$BASE_URL/api/job-postings?size=1" | python3 -c "import sys,json; data=json.load(sys.stdin); print(len(data.get('content',[])))" 2>/dev/null || echo "0")
if [ "$POSTING_COUNT" = "0" ]; then
    echo "WARNING: 등록된 공고가 없습니다. 5분 대기 후 자동 생성됩니다."
    exit 1
fi

# 동시성 테스트용 공고 ID 확보
POSTING_ID=$(curl -s "$BASE_URL/api/job-postings?size=1" | python3 -c "import sys,json; print(json.load(sys.stdin)['content'][0]['id'])" 2>/dev/null)
echo "[사전 확인] 테스트 대상 공고 ID: $POSTING_ID"
echo ""

# 시나리오 1: 동시성 검증
echo "========================================="
echo "[1/8] 조회 이력 등록 동시성 (1,000VU × 1회 = 1,000요청)"
echo "  기대: 200 OK = 100, 204 No Content = 900"
echo "========================================="
k6 run --env BASE_URL="$BASE_URL" --env POSTING_ID="$POSTING_ID" scenarios/view-registration-concurrency.js
echo ""

# 시나리오 2: 목록 API 성능
echo "========================================="
echo "[2/8] 공고 목록 API 성능 (50VU, 30초)"
echo "  기대: p(95) < 200ms"
echo "========================================="
k6 run --env BASE_URL="$BASE_URL" scenarios/job-listing-performance.js
echo ""

# 시나리오 3: 메인 페이지 고동시성
echo "========================================="
echo "[3/8] 메인 페이지 API 고동시성 (1,000VU, 30초)"
echo "  기대: p(95) < 100ms"
echo "========================================="
k6 run --env BASE_URL="$BASE_URL" scenarios/main-page-performance.js
echo ""

# 시나리오 4: 혼합 워크로드
echo "========================================="
echo "[4/8] 혼합 워크로드 (100VU, 1분)"
echo "  메인 70% / 목록 20% / 조회 등록 10%"
echo "========================================="
k6 run --env BASE_URL="$BASE_URL" scenarios/mixed-workload.js
echo ""

# 시나리오 5: 스트레스 테스트
echo "========================================="
echo "[5/8] 스트레스 테스트 (500→5,000VU 점진 증가, 2.5분)"
echo "  한계점 탐색 — 에러율/레이턴시 급등 구간 확인"
echo "========================================="
k6 run --env BASE_URL="$BASE_URL" scenarios/stress-test.js
echo ""

# 시나리오 6: DB 커넥션 풀 한계점
echo "========================================="
echo "[6/8] DB 커넥션 풀 한계점 (0→500VU, no sleep, 2분 40초)"
echo "  목록 API 100% — HikariCP 30 커넥션 포화 지점 탐색"
echo "========================================="
k6 run --env BASE_URL="$BASE_URL" scenarios/db-connection-pool-breakpoint.js
echo ""

# 시나리오 7: 쓰기 포화 + 읽기 경합
echo "========================================="
echo "[7/8] 쓰기 포화 + 읽기 경합 (500VU 쓰기 + 200VU 읽기, 2분)"
echo "  스케줄러 flush 중 읽기 레이턴시 영향 측정"
echo "========================================="
k6 run --env BASE_URL="$BASE_URL" scenarios/write-saturation-read-contention.js
echo ""

# 시나리오 8: 스파이크 테스트
echo "========================================="
echo "[8/8] 스파이크 테스트 (0→5,000VU 즉시, 1분 36초)"
echo "  점진적 증가(시나리오 5) 대비 즉시 스파이크 비교"
echo "========================================="
k6 run --env BASE_URL="$BASE_URL" scenarios/spike-test.js
echo ""

echo "=== 모든 부하 테스트 완료 ==="
