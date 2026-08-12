import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const writeDuration = new Trend('write_duration');
const readDuration = new Trend('read_duration');
const writeSuccess = new Counter('write_200_ok');
const writeRejected = new Counter('write_204_rejected');
const readErrors = new Counter('read_errors');

export const options = {
    scenarios: {
        write_flood: {
            executor: 'constant-vus',
            vus: 500,
            duration: '2m',
            exec: 'writeView',
        },
        concurrent_reads: {
            executor: 'constant-vus',
            vus: 200,
            duration: '2m',
            exec: 'readListing',
        },
    },
    thresholds: {
        write_duration: ['p(95)<100'],
        read_duration: ['p(95)<1000'],
        http_req_failed: ['rate<0.05'],
    },
};

export function writeView() {
    const postingId = Math.floor(Math.random() * 50) + 1;
    const userId = __VU * 100000 + __ITER;

    const res = http.post(
        `${BASE_URL}/api/job-postings/${postingId}/view?userId=${userId}`
    );

    writeDuration.add(res.timings.duration);

    check(res, {
        'write ok': (r) => r.status === 200 || r.status === 204,
    });

    if (res.status === 200) writeSuccess.add(1);
    if (res.status === 204) writeRejected.add(1);
}

export function readListing() {
    const res = http.get(
        `${BASE_URL}/api/job-postings?size=20`,
        { timeout: '10s' }
    );

    readDuration.add(res.timings.duration);

    const passed = check(res, {
        'listing 200': (r) => r.status === 200,
    });

    if (!passed) readErrors.add(1);
}

export function handleSummary(data) {
    const ok = data.metrics.write_200_ok
        ? data.metrics.write_200_ok.values.count : 0;
    const rejected = data.metrics.write_204_rejected
        ? data.metrics.write_204_rejected.values.count : 0;
    const readErrs = data.metrics.read_errors
        ? data.metrics.read_errors.values.count : 0;

    const rd = data.metrics.read_duration;
    const readP95 = rd && rd.values && rd.values['p(95)'] != null
        ? rd.values['p(95)'].toFixed(1) : 'N/A';
    const readP99 = rd && rd.values && rd.values['max'] != null
        ? rd.values['max'].toFixed(1) : 'N/A';

    console.log('\n========== 쓰기 포화 + 읽기 경합 결과 ==========');
    console.log(`쓰기: 200 OK=${ok}, 204 Rejected=${rejected}`);
    console.log(`  (기대: ~5,000 OK = 50 postings × 100 cap)`);
    console.log(`읽기: 에러 수=${readErrs}`);
    console.log(`읽기 레이턴시: p95=${readP95}ms, p99=${readP99}ms`);
    console.log('=================================================\n');

    return { stdout: JSON.stringify(data, null, 2) };
}
