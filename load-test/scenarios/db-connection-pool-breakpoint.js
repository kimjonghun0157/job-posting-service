import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const listingDuration = new Trend('listing_duration');
const timeoutErrors = new Counter('timeout_errors');

export const options = {
    scenarios: {
        listing_ramp: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 100 },
                { duration: '30s', target: 200 },
                { duration: '30s', target: 300 },
                { duration: '30s', target: 500 },
                { duration: '30s', target: 500 },
                { duration: '10s', target: 0 },
            ],
        },
    },
    thresholds: {
        listing_duration: ['p(95)<1000', 'p(99)<3000'],
        http_req_failed: ['rate<0.10'],
        timeout_errors: ['count<50'],
    },
};

export default function () {
    const sortOptions = ['id', 'createdAt'];
    const sort = sortOptions[Math.floor(Math.random() * sortOptions.length)];
    const direction = Math.random() > 0.5 ? 'ASC' : 'DESC';

    const res = http.get(
        `${BASE_URL}/api/job-postings?size=20&sortBy=${sort}&direction=${direction}`,
        { timeout: '10s' }
    );

    listingDuration.add(res.timings.duration);

    const passed = check(res, {
        'status 200': (r) => r.status === 200,
        'response < 5s': (r) => r.timings.duration < 5000,
    });

    if (!passed && res.timings.duration >= 10000) {
        timeoutErrors.add(1);
    }
}
