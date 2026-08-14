#!/bin/bash
#
# 부하테스트용 고정 픽스처 주입 스크립트
#
#   1. 공고를 MIN_POSTINGS건 이상 확보 (부족분만 INSERT)
#   2. 최신 100건 ID를 Redis List `main:cache`에, 제목을 Hash `main:title`에 강제 등록
#   3. 100건 각각에 대해 `view:count:{id}` 키 생성
#   4. /main 응답이 실제로 100건인지 검증
#
# 사용법:
#   ./setup-fixture.sh                # 주입 + 검증
#   ./setup-fixture.sh --verify-only  # 주입 없이 검증만
#
# !! 중요: 반드시 "앱 기동 후"에 실행할 것.
#    @Scheduled(fixedRate) 는 fixed-rate 값을 아무리 크게 줘도 기동 직후 1회는 반드시 실행된다.
#    즉 MainPageScheduler.refresh() 가 부팅 시 main:cache 를 "최신 100건" 폴백으로 덮어쓴다.
#    앱 기동 전에 주입하면 그 덮어쓰기에 지워진다. 기동 후 주입하면 다음 실행은 fixed-rate
#    (부하테스트 시 86400000 = 24시간) 뒤이므로 안전하다.
#
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
PG_CONTAINER="${PG_CONTAINER:-pickbit-postgres}"
REDIS_CONTAINER="${REDIS_CONTAINER:-pickbit-redis}"
DB_USER="${DB_USER:-pickbit_user}"
DB_NAME="${DB_NAME:-pickbit_job_db}"

CACHE_SIZE=100      # main:cache 에 넣을 공고 수 (= ViewPolicy.MAIN_PAGE_LIMIT)
MIN_POSTINGS=150    # DB에 최소 확보할 공고 수

VERIFY_ONLY=false
[ "${1:-}" = "--verify-only" ] && VERIFY_ONLY=true

psql_q() { docker exec "$PG_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -tAc "$1"; }
redis()  { docker exec "$REDIS_CONTAINER" redis-cli "$@"; }

fail() { echo "  ✗ $1"; FAILED=$((FAILED + 1)); }
ok()   { echo "  ✓ $1"; }

# ---------------------------------------------------------------- 사전 확인
echo "=== 픽스처 주입 (DB: $PG_CONTAINER / Redis: $REDIS_CONTAINER / API: $BASE_URL) ==="

if ! docker exec "$PG_CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
    echo "ERROR: Postgres 컨테이너($PG_CONTAINER)에 접속할 수 없습니다."
    exit 1
fi
if [ "$(redis PING 2>/dev/null)" != "PONG" ]; then
    echo "ERROR: Redis 컨테이너($REDIS_CONTAINER)에 접속할 수 없습니다."
    exit 1
fi

TABLE_EXISTS=$(psql_q "SELECT to_regclass('public.job_posting') IS NOT NULL;")
if [ "$TABLE_EXISTS" != "t" ]; then
    echo "ERROR: job_posting 테이블이 없습니다. 앱을 한 번 기동해 스키마를 먼저 생성하세요."
    echo "  java -jar build/libs/job-posting-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=stress"
    exit 1
fi

# ---------------------------------------------------------------- 1. 공고 시딩
if [ "$VERIFY_ONLY" = false ]; then
    CURRENT=$(psql_q "SELECT count(*) FROM job_posting;")
    echo ""
    echo "[1/3] 공고 시딩 — 현재 ${CURRENT}건 / 목표 ${MIN_POSTINGS}건"

    if [ "$CURRENT" -lt "$MIN_POSTINGS" ]; then
        NEED=$((MIN_POSTINGS - CURRENT))
        psql_q "
        INSERT INTO job_posting (id, title, company, description, view_count, created_at, updated_at)
        SELECT (SELECT COALESCE(MAX(id), 0) FROM job_posting) + i,
               '부하테스트 공고 ' || i,
               '테스트기업 ' || i,
               '부하테스트용 상세 내용 — 메인 캐시 100건 고정 픽스처',
               0,
               now(), now()
        FROM generate_series(1, ${NEED}) AS i;
        " >/dev/null
        # Hibernate SEQUENCE(allocationSize=100, pooled) 와의 PK 충돌 방지
        psql_q "SELECT setval('seq', (SELECT MAX(id) FROM job_posting) + 1000);" >/dev/null
        TOTAL=$(psql_q "SELECT count(*) FROM job_posting;")
        echo "  → ${NEED}건 추가, 총 ${TOTAL}건 (시퀀스 seq 재조정 완료)"
    else
        echo "  → 이미 충분함, INSERT 생략"
    fi

    # ------------------------------------------------------------ 2. Redis 주입
    echo ""
    echo "[2/3] Redis 주입 — main:cache/main:title ${CACHE_SIZE}건 + view:count 키 ${CACHE_SIZE}개"

    # 이전 회차 잔여 키 정리 (view:count 키 개수를 정확히 100으로 맞추기 위함)
    docker exec "$REDIS_CONTAINER" sh -c \
        "redis-cli --scan --pattern 'view:count:*' | xargs -r redis-cli DEL" >/dev/null
    docker exec "$REDIS_CONTAINER" sh -c \
        "redis-cli --scan --pattern 'view:unique:*' | xargs -r redis-cli DEL" >/dev/null
    redis DEL main:cache main:cache:tmp main:title main:title:tmp main:ranking view:queue >/dev/null

    # 최신 100건 ID (MainPageService 의 폴백 정렬 기준과 동일)
    IDS=$(psql_q "SELECT id FROM job_posting ORDER BY created_at DESC, id DESC LIMIT ${CACHE_SIZE};" | tr -d '\r')
    ID_COUNT=$(echo "$IDS" | grep -c '[0-9]')
    if [ "$ID_COUNT" -ne "$CACHE_SIZE" ]; then
        echo "ERROR: 공고 ID를 ${CACHE_SIZE}개 확보하지 못했습니다 (${ID_COUNT}개)."
        exit 1
    fi

    # main:cache 강제 등록 — RedisMainPageCache 와 동일한 키/자료형(List of string id)
    # shellcheck disable=SC2086
    redis RPUSH main:cache $(echo "$IDS" | tr '\n' ' ') >/dev/null

    # main:title 강제 등록 — Hash(field=id, value=title).
    # 이게 없으면 RedisMainPageCache.getCached() 가 전부 걸러내 /main 이 0건이 된다.
    # 제목에 공백이 있으므로 psql 출력을 그대로 HSET 인자로 넘기지 않고 한 건씩 넣는다.
    psql_q "SELECT id || E'\t' || title FROM job_posting ORDER BY created_at DESC, id DESC LIMIT ${CACHE_SIZE};" \
        | tr -d '\r' \
        | while IFS=$'\t' read -r pid ptitle; do
              [ -z "$pid" ] && continue
              printf 'HSET main:title %s %s\n' "$pid" "$(printf '%s' "$ptitle" | sed 's/"/\\"/g; s/^/"/; s/$/"/')"
          done \
        | docker exec -i "$REDIS_CONTAINER" redis-cli >/dev/null

    # view:count:{id} 생성 — 값은 1~10 (ViewPolicy.MAX_VIEW_COUNT=100 여유를 남겨
    # 조회 등록 POST 가 전부 204 로 거절되지 않도록 낮게 잡는다)
    MSET_ARGS=""
    IDX=0
    for id in $IDS; do
        VAL=$(( IDX % 10 + 1 ))
        MSET_ARGS="$MSET_ARGS view:count:$id $VAL"
        IDX=$((IDX + 1))
    done
    # shellcheck disable=SC2086
    redis MSET $MSET_ARGS >/dev/null
    echo "  → main:cache RPUSH + main:title HSET 완료, view:count 키 ${IDX}개 생성 (값 1~10)"
fi

# ---------------------------------------------------------------- 3. 검증
echo ""
echo "[3/3] 검증"
FAILED=0

# (1) main:cache 길이
LLEN=$(redis LLEN main:cache)
[ "$LLEN" = "$CACHE_SIZE" ] && ok "main:cache LLEN = $LLEN" || fail "main:cache LLEN = $LLEN (기대 $CACHE_SIZE)"

# (2) main:title 해시 크기
HLEN=$(redis HLEN main:title | tr -d '\r')
[ "$HLEN" = "$CACHE_SIZE" ] && ok "main:title HLEN = $HLEN" || fail "main:title HLEN = $HLEN (기대 $CACHE_SIZE)"

# (3) view:count 키 개수
VC_COUNT=$(docker exec "$REDIS_CONTAINER" sh -c "redis-cli --scan --pattern 'view:count:*' | wc -l" | tr -d ' \r')
[ "$VC_COUNT" = "$CACHE_SIZE" ] && ok "view:count:* 키 = ${VC_COUNT}개" || fail "view:count:* 키 = ${VC_COUNT}개 (기대 $CACHE_SIZE)"

# (3) /main 응답 건수 — 앱이 떠 있어야 확인 가능
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$BASE_URL/api/job-postings/main" 2>/dev/null)
if [ "$HTTP_CODE" != "200" ]; then
    echo "  · 앱 미기동 (HTTP ${HTTP_CODE:-none}) — /main 검증은 앱 기동 후 --verify-only 로 다시 실행하세요."
else
    BODY=$(curl -s --max-time 10 "$BASE_URL/api/job-postings/main")
    LEN=$(echo "$BODY" | jq 'length')
    [ "$LEN" = "$CACHE_SIZE" ] && ok "GET /api/job-postings/main = ${LEN}건" || fail "GET /api/job-postings/main = ${LEN}건 (기대 $CACHE_SIZE)"

    # (4) viewCount 가 Redis 값으로 채워졌는지 (0 이 아닌 건수)
    NONZERO=$(echo "$BODY" | jq '[.[] | select(.viewCount > 0)] | length')
    [ "$NONZERO" = "$CACHE_SIZE" ] && ok "viewCount > 0 인 응답 = ${NONZERO}건" || fail "viewCount > 0 = ${NONZERO}건 (기대 $CACHE_SIZE)"

    # (4-1) title 이 캐시의 실제 제목인지 (placeholder / 빈 문자열이 아닌지)
    BAD_TITLE=$(echo "$BODY" | jq '[.[] | select(.title == null or .title == "" or (.title | startswith("posting-")))] | length')
    [ "$BAD_TITLE" = "0" ] && ok "title 전건 실제 제목 (예: $(echo "$BODY" | jq -r '.[0].title'))" \
        || fail "placeholder/빈 title = ${BAD_TITLE}건"

    # (5) 응답 ID 집합 == main:cache 내용
    API_IDS=$(echo "$BODY" | jq -r '.[].id' | sort -n | tr -d '\r')
    CACHE_IDS=$(redis LRANGE main:cache 0 -1 | sort -n | tr -d '\r')
    if [ "$API_IDS" = "$CACHE_IDS" ]; then
        ok "응답 ID 집합 == main:cache 내용 일치"
    else
        fail "응답 ID 집합이 main:cache 와 불일치"
    fi
fi

echo ""
if [ "$FAILED" -eq 0 ]; then
    echo "=== 픽스처 준비 완료 ==="
    exit 0
else
    echo "=== 검증 실패 ${FAILED}건 ==="
    exit 1
fi
