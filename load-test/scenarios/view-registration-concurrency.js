import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const POSTING_ID = __ENV.POSTING_ID || '1';

const successCount = new Counter('view_200_ok');
const rejectedCount = new Counter('view_204_rejected');

export const options = {
    scenarios: {
        concurrent_views: {
            executor: 'per-vu-iterations',
            vus: 1000,
            iterations: 1,
            maxDuration: '30s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<500'],
    },
};

export default function () {
    const userId = __VU * 1000 + __ITER;
    const res = http.post(
        `${BASE_URL}/api/job-postings/${POSTING_ID}/view?userId=${userId}`
    );

    check(res, {
        'status is 200 or 204': (r) => r.status === 200 || r.status === 204,
    });

    if (res.status === 200) {
        successCount.add(1);
    } else if (res.status === 204) {
        rejectedCount.add(1);
    }
}

export function handleSummary(data) {
    const ok = data.metrics.view_200_ok ? data.metrics.view_200_ok.values.count : 0;
    const rejected = data.metrics.view_204_rejected ? data.metrics.view_204_rejected.values.count : 0;

    console.log('\n========== 동시성 검증 결과 ==========');
    console.log(`총 요청: ${ok + rejected} (VU 1,000 × 1회)`);
    console.log(`200 OK (등록 성공): ${ok}`);
    console.log(`204 No Content (상한 초과): ${rejected}`);
    console.log(`기대값: 200 OK = 100, 204 = 900`);
    console.log(`결과: ${ok === 100 ? 'PASS' : 'FAIL'}`);
    console.log('======================================\n');

    return {
        stdout: JSON.stringify(data, null, 2),
    };
}
