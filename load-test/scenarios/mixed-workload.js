import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    scenarios: {
        mixed: {
            executor: 'constant-vus',
            vus: 100,
            duration: '1m',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],
        http_req_failed: ['rate<0.01'],
    },
};

export default function () {
    const roll = Math.random();

    if (roll < 0.7) {
        const res = http.get(`${BASE_URL}/api/job-postings/main`);
        check(res, { 'main 200': (r) => r.status === 200 });
    } else if (roll < 0.9) {
        const res = http.get(`${BASE_URL}/api/job-postings?size=20`);
        check(res, { 'list 200': (r) => r.status === 200 });
    } else {
        const postingId = randomIntBetween(1, 50);
        const userId = __VU * 10000 + __ITER;
        const res = http.post(
            `${BASE_URL}/api/job-postings/${postingId}/view?userId=${userId}`
        );
        check(res, {
            'view 200 or 204': (r) => r.status === 200 || r.status === 204,
        });
    }

    sleep(0.1);
}
