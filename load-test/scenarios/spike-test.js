import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const spikeDuration = new Trend('spike_duration');
const connectionRefused = new Counter('connection_refused');

export const options = {
    scenarios: {
        spike: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1s', target: 5000 },
                { duration: '30s', target: 5000 },
                { duration: '30s', target: 1000 },
                { duration: '30s', target: 1000 },
                { duration: '5s', target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.15'],
        spike_duration: ['p(95)<3000'],
        connection_refused: ['count<500'],
    },
};

export default function () {
    const roll = Math.random();
    let res;

    if (roll < 0.7) {
        res = http.get(`${BASE_URL}/api/job-postings/main`, { timeout: '15s' });
        check(res, { 'main 200': (r) => r.status === 200 });
    } else if (roll < 0.9) {
        res = http.get(`${BASE_URL}/api/job-postings?size=20`, { timeout: '15s' });
        check(res, { 'list 200': (r) => r.status === 200 });
    } else {
        const postingId = Math.floor(Math.random() * 50) + 1;
        const userId = __VU * 10000 + __ITER;
        res = http.post(
            `${BASE_URL}/api/job-postings/${postingId}/view?userId=${userId}`,
            null,
            { timeout: '15s' }
        );
        check(res, {
            'view ok': (r) => r.status === 200 || r.status === 204,
        });
    }

    spikeDuration.add(res.timings.duration);

    if (res.error && res.error.includes('connection refused')) {
        connectionRefused.add(1);
    }

    sleep(0.05);
}

export function handleSummary(data) {
    const p50 = data.metrics.spike_duration
        ? data.metrics.spike_duration.values['p(50)'].toFixed(1) : 'N/A';
    const p95 = data.metrics.spike_duration
        ? data.metrics.spike_duration.values['p(95)'].toFixed(1) : 'N/A';
    const p99 = data.metrics.spike_duration
        ? data.metrics.spike_duration.values['p(99)'].toFixed(1) : 'N/A';
    const errRate = data.metrics.http_req_failed
        ? (data.metrics.http_req_failed.values.rate * 100).toFixed(2) : 'N/A';
    const connRefused = data.metrics.connection_refused
        ? data.metrics.connection_refused.values.count : 0;

    console.log('\n========== Spike Test 결과 ==========');
    console.log(`레이턴시: p50=${p50}ms, p95=${p95}ms, p99=${p99}ms`);
    console.log(`에러율: ${errRate}%`);
    console.log(`Connection Refused: ${connRefused}건`);
    console.log(`비교: 기존 Stress Test p95=198ms @ 5,000 VU (점진적 증가)`);
    console.log('=======================================\n');

    return { stdout: JSON.stringify(data, null, 2) };
}
