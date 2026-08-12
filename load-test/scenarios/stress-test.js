import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    scenarios: {
        stress: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 500 },
                { duration: '30s', target: 1000 },
                { duration: '30s', target: 2000 },
                { duration: '30s', target: 3000 },
                { duration: '30s', target: 5000 },
                { duration: '10s', target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
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
        const postingId = Math.floor(Math.random() * 50) + 1;
        const userId = __VU * 10000 + __ITER;
        const res = http.post(
            `${BASE_URL}/api/job-postings/${postingId}/view?userId=${userId}`
        );
        check(res, {
            'view ok': (r) => r.status === 200 || r.status === 204,
        });
    }

    sleep(0.05);
}
