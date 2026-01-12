# 트래픽 호출 테스트 명세서

본 문서는 HeavenlyCoupon 프로젝트에서 **외부 부하 도구(k6)**로
`/api/v1/coupons/issue`를 호출하는 트래픽 테스트 기준을 정의합니다.

---

## 1. 목적

- 초당 60~70만 QPS 구간에서 API 수용 한계를 확인합니다.
- Redis/Lua/Queue 적용 전, **기준선(Baseline)** 성능을 확보합니다.
- 실패/지연/에러율 기준을 정해 비교 가능한 결과를 만든다.

---

## 2. API 계약(고정)

### 2.1 Endpoint
```
POST /api/v1/coupons/issue
```

### 2.2 Request Body
```json
{
  "userId": "string",
  "requestId": "uuid"
}
```

### 2.3 Response
- Status Code: `202 Accepted`
- Body: 없음

---

## 3. 트래픽 생성 방식

### 3.1 도구
- k6

### 3.2 기본 실행 (10초)
```
TARGET_RPS=600000 DURATION=10s BASE_URL=http://localhost:18024 k6 run loadtest/traffic.js
```

### 3.3 환경 변수
- `TARGET_RPS`: 목표 QPS (초당 요청 수)
- `DURATION`: 유지 시간 (예: `10s`, `30s`)
- `BASE_URL`: 대상 서버 URL

---

## 4. 테스트 기준

### 4.1 목표
- 10초 동안 목표 QPS를 안정적으로 유지

### 4.2 성공 기준
- 5xx 오류율 0.1% 이하
- 타임아웃/커넥션 에러 급증 없음

### 4.3 실패 기준
- 목표 QPS 유지 불가(지속 하락)
- 서버 OOM/스레드 고갈/GC 급증
- 네트워크 오류 급증

---

## 5. 수집 지표(최소)

- 처리량(QPS), 실패율
- 평균 지연시간, P95/P99
- CPU, 메모리, GC
- 네트워크 수신/송신 대역폭

---

## 6. 결과 기록

- 테스트 환경(서버 스펙, JVM 옵션, 컨테이너 리소스)
- 달성 QPS 및 지연 분포
- 병목 지점 추정
- 다음 단계(Redis/Lua/Queue) 비교 기준선 요약
