import http from 'k6/http';
import { sleep } from 'k6';

const TARGET_RPS = Number(__ENV.TARGET_RPS || '100000');
const DURATION = __ENV.DURATION || '10s';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:18024';

const ISSUER_PATH = '/api/v1/coupons/issue';
const URL = `${BASE_URL}${ISSUER_PATH}`;

export const options = {
  scenarios: {
    coupon_issue: {
      executor: 'constant-arrival-rate',
      rate: TARGET_RPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.min(Math.max(Math.ceil(TARGET_RPS / 1000), 1000), 50000),
      maxVUs: Math.min(Math.max(Math.ceil(TARGET_RPS / 500), 2000), 100000),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

function randomUuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const rand = (Math.random() * 16) | 0;
    const value = char === 'x' ? rand : (rand & 0x3) | 0x8;
    return value.toString(16);
  });
}

export default function () {
  const payload = JSON.stringify({
    userId: `u-${__VU}-${__ITER}`,
    requestId: randomUuid(),
  });
  const headers = { 'Content-Type': 'application/json' };
  http.post(URL, payload, { headers });
  sleep(0.001);
}
