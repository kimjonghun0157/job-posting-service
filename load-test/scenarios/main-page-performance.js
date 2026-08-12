import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    scenarios: {
        main_page: {
            executor: 'constant-vus',
            vus: 1000,
            duration: '30s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<100', 'p(99)<300'],
        http_req_failed: ['rate<0.01'],
    },
};

export default function () {
    const res = http.get(`${BASE_URL}/api/job-postings/main`);

    check(res, {
        'status 200': (r) => r.status === 200,
        'is array': (r) => Array.isArray(JSON.parse(r.body)),
    });

    sleep(0.1);
}
