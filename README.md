# HeavenlyCoupon

대용량 실시간 쿠폰 발행에서 **정합성(초과 발행/중복 발행 방지)**과 **안정성(장애·재시도·유실 대응)**을 우선으로 설계한 실습용 프로젝트입니다.  
쿠폰 발행 요청은 고트래픽을 전제로 하며, 발행 “신청”과 발행 “확정”을 분리하고, Redis 원자 연산 + Kafka 이벤트 기반 처리를 통해 처리량과 일관성을 확보합니다.

---


## 1. 프로젝트 목표 및 테스트 조건

본 프로젝트 **HeavenlyCoupon**의 1차 목표는 “대용량 실시간 쿠폰 발행 환경에서 정합성과 안정성을 검증할 수 있는 아키텍처를 직접 설계·검증”하는 것입니다.

### 1.1 성능 및 부하 목표

- **초당 60~70만 TPS 수준의 쿠폰 발행 요청 부하 테스트**
- 초기 테스트 기준 **총 쿠폰 수량: 10,000,000(천만)**
- API Gateway + Load Balancer 환경에서 수평 확장 전제

### 1.2 비즈니스 제약 조건

- **사용자당 쿠폰 발행은 반드시 1회**
- 총 발행 수량 초과 발행 **절대 불가**
- 중복 요청, 재시도, 동시 요청 상황에서도 결과 일관성 보장

### 1.3 설계 및 구현 원칙

- **TDD(Test-Driven Development)** 기반 설계
- 발행 “신청”과 “실제 발행 확정”을 분리한 비동기 구조
- Redis를 1차 정합성 보장 계층으로 활용
- 장애 상황에서도 데이터 불일치가 누적되지 않도록 설계

---

## 2. 쿠폰 발행 전략 비교

대용량 트래픽 상황에서 정합성을 확보하기 위해 아래 **3가지 쿠폰 발행 전략**을 비교·검증합니다.

### 2.1 Redis 기본 연산 기반 발행 (DECR 방식)

- Redis Key  
  - `coupon:{couponId}:stock = 10000000`
- 발행 흐름
  1. `DECR`로 재고 감소
  2. 감소 후 반환된 값이 0 이상인지 확인하여 **유효성(valid) 판단**
  3. 유효할 경우 사용자 발행 처리
- Redis 명령은 단일 스레드로 처리되므로 **재고 초과 발행은 발생하지 않음**

> 단, 사용자 중복 발행 체크와 재고 감소가 분리될 경우 경쟁 조건 가능성이 존재하므로
> 이 방식은 **기본 비교 대상**으로 사용합니다.

---

### 2.2 Redis Lua Script 기반 발행

- Redis Lua Script를 이용해 **단일 스크립트(1 Transaction)** 로 처리
- 하나의 호출에서 아래 로직을 **원자적으로 수행**
  1. 사용자 중복 발행 여부 확인
  2. 재고 확인
  3. 재고 감소(DECR)
  4. 발행 성공 시 사용자 마킹
- 장점
  - 중복 발행 방지 + 재고 차감 + 검증을 한 번에 처리
  - 네트워크 왕복 감소
  - 고동시성 환경에서 정합성 확보에 가장 유리

---

### 2.3 사전 큐 기반 발행 (Pre-generated Coupon Queue)

- **쿠폰 UUID 10,000,000개를 사전에 생성**
- Redis Queue(List/Stream)에 적재
- 발행 시 `POP` 수행
  - `POP` 성공 → 발행 가능(valid)
  - `POP` 실패 → 재고 소진
- 장점
  - 발행 가능 수량 자체가 큐 크기로 제한됨
- 검토 포인트
  - 사용자 중복 발행 제어
  - 재시도 시 중복 소비 방지
  - 실제 발행 확정(DB)과의 정합성

---

## 3. 쿠폰 발행 이후 처리 전략

### 3.1 Kafka 기반 비동기 발행 확정

- 쿠폰 발행 요청이 Redis 단계에서 성공하면 **Kafka Event 발행**
- 이벤트 예시
  - `couponId`
  - `userId`
  - `issuedSequence`
  - `requestId`

### 3.2 Kafka 사용 목적

- 대량 트래픽 상황에서 API 응답을 빠르게 반환하기 위함
- 실제 쿠폰 지급, 알림, 마이페이지 반영을 **비동기 처리**
- 발행 처리와 후속 작업 간 결합도 제거

### 3.3 메시지 유실 및 중복 대응

- Kafka는 **at-least-once** 전달을 전제로 설계
- 대응 전략
  - 이벤트 발행 전 또는 직후 DB에 발행 상태 기록
  - Consumer 단에서 `requestId` 기준 멱등 처리
  - 필요 시 Outbox 패턴 적용

---

## 4. API 응답 정책

- 쿠폰 발행 요청 성공 시
  - **HTTP 202 Accepted**
  - 의미: “발행 신청 접수 완료”
- 실제 발행 결과는
  - Kafka Consumer 처리 후
  - 마이페이지 반영 / 알림 / 상태 조회 API 등을 통해 사용자에게 제공


## 4. 핵심 설계 포인트

### 4.1 정합성 보장 전략(Redis 중심)

#### 공통 키 설계(예시)

- 재고 카운터
  - `coupon:{couponId}:stock = 1000000`
- 사용자 중복 발행 방지
  - `coupon:{couponId}:users` (Set) 또는
  - `coupon:{couponId}:user:{userId}` (String/Marker, TTL 옵션)
- 요청 멱등성(권장)
  - `coupon:{couponId}:req:{requestId}` (처리 결과 캐시)

> 운영 관점에서는 “**쿠폰별 사용자 Set**”이 크면 메모리 사용량이 증가하므로,  
> **Bloom Filter + 확정 DB 검증** 또는 “쿠폰별 user marker” 등 대안을 함께 검토합니다.

---

### 4.2 방식 1) Redis 명령 조합(분리 호출) — 단순하지만 경쟁 조건 주의

예시 흐름:

1) `SISMEMBER coupon:{couponId}:users {userId}` 로 중복 확인  
2) `DECR coupon:{couponId}:stock` 로 재고 차감  
3) 재고가 음수가 되면 `INCR` 롤백 및 실패 처리  
4) 성공 시 `SADD coupon:{couponId}:users {userId}` 저장

**리스크**
- 분리 호출 사이에 경쟁 조건이 발생할 수 있으며,
- 네트워크 지연/장애 시 “차감만 되고 사용자 기록이 누락” 같은 부분 실패 가능성이 있습니다.

> 따라서 대용량·고동시성에서는 아래 Lua Script 방식(1회 호출)을 기본으로 권장합니다.

---

### 4.3 방식 2) Redis Lua Script 1회 호출 — 원자적 처리

**목표:** “중복 확인 + 재고 확인/차감 + 사용자 기록”을 **단일 원자 연산**으로 수행합니다.

#### Lua Script 의사 로직(개념)

- 입력: `couponId`, `userId`, `requestId`
- 처리:
  1) 이미 처리된 `requestId`면 기존 결과 반환(멱등)
  2) `userId`가 이미 발행 받았는지 확인 → 있으면 중복 실패 반환
  3) 재고 조회 → 0 이하이면 실패 반환
  4) `DECR` 후 0 미만이면 롤백하고 실패 반환
  5) 사용자 발행 마커 기록
  6) 발행 순번(issuedSeq) 부여(옵션: INCR seq 키)
  7) 결과 저장(requestId 캐시) 후 성공 반환

**장점**
- 원자성 확보 → 초과 발행/중복 발행 방지에 유리
- 애플리케이션 레벨 락 최소화

---

### 4.4 (검토) 방식 3) 사전 큐(Pre-generated UUID Queue)

- “발행 가능한 쿠폰 UUID”를 미리 `LIST/STREAM` 등에 적재해두고 `POP`으로 할당하는 방식입니다.
- POP 자체는 원자적이나, 사용자 중복/재시도/확정 DB 정합성을 함께 고려해야 합니다.

> 본 프로젝트에서는 우선 Redis Lua Script 방식으로 정합성 확보 후,  
> 대안으로 큐 방식의 장단점을 비교·확장할 수 있도록 구성합니다.

---

## 5. Redis Cluster 및 가용성 고려사항

### 5.1 Cluster Key 설계

- `coupon:{couponId}:stock` 처럼 **중괄호 해시태그**를 활용하면,
  동일 `couponId` 관련 키가 같은 해시 슬롯으로 모여 Lua Script/멀티키 연산에 유리합니다.
- 예:  
  - `coupon:{123}:stock`  
  - `coupon:{123}:users`  
  - `coupon:{123}:seq`

### 5.2 Master-Replica 장애 시 정합성

- Redis Replication은 보통 비동기입니다.
- Master 장애 시 Replica 승격 과정에서 **복제 지연분만큼 데이터 유실/역전** 가능성이 있습니다.

**대응 방향**
- “발행 시점 Redis 결과”는 빠른 응답을 위한 1차 정합성으로 사용
- 최종 확정은 DB에 기록하고, 장애 시 “DB가 최종 진실(Source of Truth)”이 되도록 설계
- 필요 시 Redis `WAIT`(복제 ACK 대기) 또는 Sentinel/Cluster 설정을 통해 trade-off를 조정

---

## 6. Kafka 이벤트 처리 및 유실/중복 대응

### 6.1 이벤트 모델(예시)

- Topic: `coupon.issue.requested`
- Payload:
  - `requestId` (필수, 멱등 키)
  - `couponId`
  - `userId`
  - `issuedSeq` (옵션)
  - `requestedAt`

### 6.2 메시지 유실/중복을 전제로 한 처리

Kafka는 “적어도 한 번(at-least-once)” 전달이 흔한 기본 전제이므로,
Consumer는 다음을 준수해야 합니다.

- **Consumer 멱등 처리**
  - DB에 `requestId` 유니크 제약을 두고 중복 insert를 방지
- **Outbox 패턴(권장)**
  - “DB에 발행 확정 기록”과 “이벤트 발행”을 원자적으로 묶기 위해,
  - `coupon_issue_outbox` 테이블에 이벤트를 먼저 적재 후 별도 릴레이가 Kafka로 발행

> 본 프로젝트에서는 “이벤트 발행 전 DB 저장” 또는 “Outbox” 중 하나를 기본 옵션으로 제공합니다.

---

## 7. API 설계(초안)

### 7.1 쿠폰 발행 요청

- `POST /api/v1/coupons/{couponId}/issue`
- Request Body
  - `userId` (필수)
  - `requestId` (필수, 클라이언트 생성 UUID 권장)

### 7.2 응답 정책(권장)

- **202 Accepted**
  - 의미: “발행 신청 접수 완료(비동기 처리)”
  - 반환: `requestId`, `status=ACCEPTED`
- **409 Conflict**
  - 이미 발행 받은 사용자(중복 요청)
- **410 Gone / 409**
  - 재고 소진(정책에 따라 선택)
- **429 Too Many Requests**
  - 사용자/아이피 단위 레이트리밋 초과(게이트웨이 또는 API 레벨)

### 7.3 사용자에게 “최종 발행 결과” 전달 방식

- 선택지
  - `GET /api/v1/issue-requests/{requestId}` 로 상태 조회(Polling)
  - WebSocket/SSE로 상태 푸시
  - 알림(메일/푸시) 및 마이페이지 반영

---

## 8. 로컬 실행(Docker)

> 실제 `docker-compose.yml` 및 각 서비스 구성은 추후 커밋 기준으로 갱신합니다.

예시 구성:
- `heavenlycoupon-api` (Java 25)
- `redis` 또는 `redis-cluster`
- `kafka` + `zookeeper`(또는 KRaft)

---

## 9. 폴더 구조(예시)

```
HeavenlyCoupon/
├── docs
│   ├── specs
│   │   ├── coupon-issue.md          # 쿠폰 발행 비즈니스 명세 (최상위)
│   │   ├── redis-strategy.md        # Redis / Lua / Queue 전략 비교
│   │   ├── kafka-event.md           # 이벤트 모델 & 보장 수준
│   │   └── failure-scenarios.md     # 장애/중복/유실 시나리오
│   └── architecture
│       ├── sequence.md              # 시퀀스 다이어그램 설명
│       └── decision-log.md          # 왜 이 선택을 했는가
│
├── src
│   ├── main
│   │   └── java
│   └── test
│       ├── specs                    # 테스트용 명세 DSL / 픽스처
│       ├── coupon
│       │   ├── CouponIssueSpecTest
│       │   ├── RedisDecrTest
│       │   ├── LuaScriptTest
│       │   └── QueuePopTest
└─ README.md
```

---

## 10. 운영 체크리스트(요약)

- [ ] API 레이트 리밋(게이트웨이/로드밸런서)
- [ ] Redis 키 설계(해시 슬롯/TTL/메모리 사용량)
- [ ] Lua Script 원자 처리 및 장애 시 재시도 전략
- [ ] DB 유니크 제약(중복 발행 방지)
- [ ] Kafka Consumer 멱등 처리(requestId 기준)
- [ ] Outbox 또는 “DB 저장 후 이벤트 발행” 패턴 적용
- [ ] 모니터링(발행 성공/실패율, Redis latency, Kafka lag)

---

## 11. 라이선스

- 개인 학습/면접 대비 목적의 예제 프로젝트입니다. (추후 명시 예정)
