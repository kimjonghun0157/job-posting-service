#!/bin/bash
#
# 메인 캐시 100건 고정 상태에서 1,000 → 5,000 → 10,000 VU 순차 부하테스트
#
# 사전 조건 (순서 중요):
#   1. docker stop wsa-kafka-ui        (8080 포트 확보)
#   2. ./gradlew clean bootJar
#   3. java -Xms4g -Xmx4g -XX:+UseG1GC -jar build/libs/job-posting-service-0.0.1-SNAPSHOT.jar \
#        --spring.profiles.active=stress \
#        --scheduler.main-page.fixed-rate=86400000 \
#        --scheduler.job-posting.fixed-rate=86400000
#   4. ./load-test/setup-fixture.sh    <- 반드시 앱 기동 "후". 기동 직후 스케줄러가 1회 실행되어
#                                         main:cache 를 덮어쓰므로 그 뒤에 주입해야 한다.
#   5. sudo sysctl -w kern.ipc.somaxconn=1024 net.inet.ip.portrange.first=16384
#
set -uo pipefail

cd "$(dirname "$0")" || exit 1

BASE_URL="${BASE_URL:-http://localhost:8080}"
REDIS_CONTAINER="${REDIS_CONTAINER:-pickbit-redis}"
PG_CONTAINER="${PG_CONTAINER:-pickbit-postgres}"
DURATION="${DURATION:-30s}"
COOLDOWN="${COOLDOWN:-60}"
VU_LIST="${VU_LIST:-1000 5000 10000}"
RESULTS_DIR="results"

mkdir -p "$RESULTS_DIR"

echo "=== 메인 페이지 VU 스케일링 부하테스트 ==="
echo "  대상   : $BASE_URL"
echo "  VU     : $VU_LIST"
echo "  지속   : $DURATION (회차 간 쿨다운 ${COOLDOWN}초)"
echo ""

# 커널 파라미터 현황 안내 (10,000 VU 에서 중요)
echo "[환경] somaxconn=$(sysctl -n kern.ipc.somaxconn) ephemeral_first=$(sysctl -n net.inet.ip.portrange.first) ulimit_n=$(ulimit -n)"
if [ "$(sysctl -n kern.ipc.somaxconn)" -lt 1024 ]; then
    echo "  ! 경고: somaxconn 이 낮아 Tomcat accept-count(200)가 잘립니다."
    echo "    sudo sysctl -w kern.ipc.somaxconn=1024 net.inet.ip.portrange.first=16384"
fi
echo ""

# 사전 확인: 앱 기동 + 메인 캐시 100건
if [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$BASE_URL/api/job-postings/main")" != "200" ]; then
    echo "ERROR: 앱이 응답하지 않습니다 ($BASE_URL)."
    exit 1
fi

for VUS in $VU_LIST; do
    # 회차 시작 전 캐시 상태 재확인 (스케줄러가 켜져 있으면 100건이 깨진다)
    LLEN=$(docker exec "$REDIS_CONTAINER" redis-cli LLEN main:cache | tr -d '\r')
    API_LEN=$(curl -s --max-time 10 "$BASE_URL/api/job-postings/main" | jq 'length')
    if [ "$LLEN" != "100" ] || [ "$API_LEN" != "100" ]; then
        echo "ERROR: 메인 캐시가 100건이 아닙니다 (main:cache=$LLEN, /main=$API_LEN)."
        echo "       ./setup-fixture.sh 재실행 후, 앱이 --scheduler.main-page.fixed-rate=86400000 로"
        echo "       기동됐는지 확인하세요."
        exit 1
    fi

    echo "========================================="
    echo "[${VUS} VU] main:cache=100 확인 — 시작"
    echo "========================================="

    VH_BEFORE=$(docker exec "$PG_CONTAINER" psql -U pickbit_user -d pickbit_job_db -tAc \
        "SELECT count(*) FROM view_history;" 2>/dev/null | tr -d '\r')

    k6 run \
        --env BASE_URL="$BASE_URL" \
        --env VUS="$VUS" \
        --env DURATION="$DURATION" \
        --summary-trend-stats 'avg,min,med,max,p(90),p(95),p(99)' \
        --summary-export "$RESULTS_DIR/summary-main-${VUS}vu.json" \
        scenarios/main-page-vu-scaling.js 2>&1 | tee "$RESULTS_DIR/console-main-${VUS}vu.log"

    K6_EXIT=${PIPESTATUS[0]}

    # 서버 측 지표 스냅샷
    VH_AFTER=$(docker exec "$PG_CONTAINER" psql -U pickbit_user -d pickbit_job_db -tAc \
        "SELECT count(*) FROM view_history;" 2>/dev/null | tr -d '\r')
    {
        echo "--- 서버 측 지표 (${VUS} VU 종료 직후) ---"
        echo "k6 exit code       : $K6_EXIT"
        echo "view_history rows  : $VH_BEFORE -> $VH_AFTER"
        docker exec "$REDIS_CONTAINER" redis-cli INFO stats | grep -E 'instantaneous_ops_per_sec|total_commands_processed|rejected_connections'
        docker exec "$REDIS_CONTAINER" redis-cli INFO clients | grep -E 'connected_clients|blocked_clients'
    } | tee -a "$RESULTS_DIR/console-main-${VUS}vu.log"

    echo ""
    if [ "$VUS" != "${VU_LIST##* }" ]; then
        echo "쿨다운 ${COOLDOWN}초 (TIME_WAIT 소켓 회수 + JVM 안정화)..."
        sleep "$COOLDOWN"
        echo ""
    fi
done

echo "=== 완료 — 결과: $(pwd)/$RESULTS_DIR ==="
ls -la "$RESULTS_DIR"
