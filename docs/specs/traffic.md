# 트래픽 호출 테스트(기준선) 명세서 – TrafficOnlyProcessor

본 문서는 HeavenlyCoupon 프로젝트에서 **“초당 60~70만 트래픽 호출 테스트”**를 수행하기 위한
**TrafficOnlyProcessor 단계(기준선/Baseline) 명세**를 정의합니다.

- 본 단계는 Redis/Lua/Queue 적용 전, **API 경로와 애플리케이션 수용 한계**를 먼저 확정하기 위한 단계입니다.
- 이후 RedisDecrProcessor / RedisLuaProcessor / QueueProcessor는 **동일한 API 계약**을 유지한 채 내부 구현만 교체합니다.

---

## 1. 목적

1) 초당 600,000 ~ 700,000 수준의 요청을 **운영 API 형태로 수신/응답**할 수 있는지 검증합니다.  
2) Redis/Lua/Queue 전략 비교의 기준선(Baseline)을 확보합니다.  
3) 병목 지점을 식별하기 위한 최소 계측 지표를 정의합니다.

---

## 2. 범위

### 2.1 포함(In Scope)
- 단일 외부 API 엔드포인트 제공
- 요청 수신 및 즉시 응답(비즈니스 로직 미포함)
- TrafficOnlyProcessor를 통한 최소 처리(카운팅 등)
- TPS/RPS 및 지연시간 측정(기본 지표)

### 2.2 제외(Out of Scope)
- Redis 연동(재고 차감/중복 체크)
- Lua Script 원자 처리
- 사전 UUID Queue/POP 방식
- Kafka 이벤트 발행 및 후속 처리
- DB 저장 및 Outbox

---

## 3. API 계약(고정)

### 3.1 Endpoint

```
POST /api/v1/coupons/issue
```

### 3.2 Request Body

```json
{
  "userId": "string",
  "requestId": "uuid"
}
```

- `userId`: 테스트용 사용자 식별자(문자열)
- `requestId`: 요청 식별자(UUID). 향후 멱등성/중복 방지의 기준 키로 사용 예정

### 3.3 Response

- Status Code: `202 Accepted`
- Body: 없음(또는 최소 JSON 1개 필드 수준으로 축소 가능)

권장 응답(예시):

```json
{
  "status": "ACCEPTED"
}
```

> 본 단계에서는 “발행 확정”이 아닌 “요청 접수”를 의미하므로 202를 기본으로 사용합니다.

---

## 4. 아키텍처 및 컴포넌트 계약

### 4.1 컴포넌트 역할

- **Controller**
  - 요청 검증(최소) 후 Facade로 위임
  - 전략 선택 로직을 포함하지 않음(단일 책임)

- **Facade**
  - Processor(전략) 실행의 진입점
  - 향후 프로세서 교체/선택 로직의 중앙화 지점

- **Processor (전략)**
  - CouponRequest를 입력받아 CouponRequestResult를 반환
  - 동일 인터페이스로 TrafficOnly / RedisDecr / RedisLua / Queue 구현을 교체 가능

### 4.2 도메인/전략 인터페이스(고정)

- `CouponRequestProcessor#process(CouponRequest) -> CouponRequestResult`
- `CouponRequest`: 불변 값 객체(record)
- `CouponRequestResult`: 불변 값 객체(record)
- 모니터링 메서드: `getTotalRequests()` (TrafficOnlyProcessor 내부 카운터 조회 용도)

---

## 5. TrafficOnlyProcessor 동작 명세

### 5.1 목적
- 요청당 최소 연산만 수행하여 API 경로의 처리 한계를 측정합니다.

### 5.2 동작 규칙
- 요청 1건 수신 시
  - 내부 카운터 1 증가(권장: `LongAdder`)
  - 즉시 `CouponRequestResult.accepted()` 반환
- 어떠한 외부 I/O도 수행하지 않습니다.
  - Redis/DB/Kafka/파일/네트워크 호출 금지
- 블로킹 작업 금지
  - lock/synchronized 사용 금지
  - sleep 금지

### 5.3 스레드/동시성 요구사항
- 고경합 환경에서 카운터 증가가 병목이 되지 않아야 합니다.
- 요청 객체는 불변(record)으로 전달되어야 합니다.

---

## 6. 트래픽 호출 테스트 요구사항

### 6.1 목표 TPS
- 초당 600,000 ~ 700,000 TPS 구간에서 안정적으로 요청 수신/응답

### 6.2 단계적 램프업(권장)
- 100k → 300k → 500k → 700k 순으로 점진 증가
- 각 구간 30초 이상 유지 후 지표 기록

### 6.3 성공 기준
- 목표 TPS 구간에서 요청 처리 지속 가능
- 5xx 오류율 0.1% 이하(권장)
- 타임아웃/커넥션 에러가 급증하지 않을 것

### 6.4 실패 기준
- TPS 유지 불가(지속 하락)
- 서버 OOM/스레드 고갈/GC 과도 증가
- 타임아웃 다수 발생

---

## 7. 수집 지표(최소)

### 7.1 API 서버
- TPS/RPS
- 평균 지연시간 / P95 / P99
- CPU 사용률
- JVM GC 횟수 및 시간
- 메모리 사용량(Heap/Non-Heap)

### 7.2 네트워크
- 수신/송신 대역폭
- 커넥션 수(활성/대기)
- 에러율(ECONNRESET 등)

---

## 8. 산출물

- 테스트 환경 정보(서버 스펙, JVM 옵션, 컨테이너 리소스 제한)
- 최대 안정 TPS 및 해당 구간의 P95/P99
- 병목 지점 추정(예: GC, 네트워크, 스레드풀, 커넥션 제한)
- 다음 단계(RedisDecrProcessor 적용)로 넘어가기 위한 기준선 요약

---

## 9. 변경 관리

- 본 문서의 API 계약(Endpoint/Request/Response)은 이후 단계에서도 유지합니다.
- 변경이 필요한 경우, **명세서 우선 수정 → 테스트 반영 → 코드 변경** 순서를 필히 준수합니다.
