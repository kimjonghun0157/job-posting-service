import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * 메인 페이지 VU 스케일링 시나리오 (1,000 / 5,000 / 10,000 VU)
 *
 * 전제: main:cache 에 정확히 100건이 고정되어 있어야 한다 (load-test/setup-fixture.sh).
 *       setup() 에서 100건이 아니면 부하를 시작하지 않고 즉시 중단한다.
 *
 * 실행: k6 run --env VUS=10000 --env BASE_URL=http://localhost:8080 main-page-vu-scaling.js
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 1000);
const DURATION = __ENV.DURATION || '30s';
const EXPECTED_COUNT = 100;

export const options = {
    // VU당 메모리/CPU 절감 — 10,000 VU 에서 클라이언트가 먼저 죽는 것을 막는다
    discardResponseBodies: true,
    scenarios: {
        main_page: {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
            gracefulStop: '30s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        // 기록용 임계치 — 초과해도 abortOnFail 없이 측정을 끝까지 수행한다
        http_req_duration: ['p(95)<1000', 'p(99)<3000'],
    },
};

export function setup() {
    // 전역 discardResponseBodies 를 요청 단위 responseType 으로 덮어써서 바디를 확인한다
    const res = http.get(`${BASE_URL}/api/job-postings/main`, { responseType: 'text' });

    if (res.status !== 200) {
        throw new Error(`/main 응답 코드가 200이 아님: ${res.status}`);
    }

    const body = JSON.parse(res.body);
    if (!Array.isArray(body)) {
        throw new Error('/main 응답이 배열이 아님');
    }
    if (body.length !== EXPECTED_COUNT) {
        throw new Error(
            `메인 캐시가 ${EXPECTED_COUNT}건이 아님: ${body.length}건. ` +
            `load-test/setup-fixture.sh 를 먼저 실행하고, 앱을 ` +
            `--scheduler.main-page.fixed-rate=86400000 으로 기동했는지 확인하세요.`
        );
    }

    console.log(`[setup] 메인 캐시 ${body.length}건 확인 — ${VUS} VU / ${DURATION} 부하 시작`);
    return { count: body.length, vus: VUS };
}

export default function () {
    const res = http.get(`${BASE_URL}/api/job-postings/main`);

    check(res, { 'status 200': (r) => r.status === 200 });

    sleep(0.1);
}
