import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    scenarios: {
        listing: {
            executor: 'constant-vus',
            vus: 50,
            duration: '30s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<200', 'p(99)<500'],
        http_req_failed: ['rate<0.01'],
    },
};

export default function () {
    let url = `${BASE_URL}/api/job-postings?size=20`;
    const firstPage = http.get(url);

    check(firstPage, {
        'first page 200': (r) => r.status === 200,
        'has content': (r) => JSON.parse(r.body).content.length > 0,
    });

    const body = JSON.parse(firstPage.body);
    if (body.hasNext && body.lastId) {
        const nextPage = http.get(`${url}&cursorId=${body.lastId}`);
        check(nextPage, {
            'next page 200': (r) => r.status === 200,
        });
    }

    sleep(0.1);
}
