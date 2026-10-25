# event-support — 서비스 사이 이벤트 계약

서비스는 서로를 직접 부르지 않고 Kafka 이벤트로 사실을 알린다. 발행은 `outbox-support`(Transactional Outbox)가,
받는 쪽 공통 처리와 **이벤트 형식(계약)** 은 이 모듈이 맡는다.

## 메시지

모든 메시지는 같은 봉투에 담긴다 ([envelope.schema.json](src/main/resources/event-schemas/envelope.schema.json)).

```json
{"eventId": "uuid", "eventType": "USER_PROFILE_UPDATED", "occurredAt": "2026-10-26T00:00:00Z",
 "aggregateType": "USER", "aggregateId": "7", "payload": {"userId": 7, "name": "새이름"}}
```

- 메시지 키는 `aggregateId` — 같은 대상의 이벤트는 같은 파티션에 순서대로 들어간다.
- 전달은 **최소 1회** — 소비자는 같은 이벤트가 다시 와도 결과가 같게 처리한다(멱등).
- 처리에 실패하면 1초 간격으로 3번 다시 시도하고 `{토픽}.DLT` 로 보낸다. 깨진 JSON·필드 누락은 바로 DLT.

## 이벤트 목록

| 토픽 | 이벤트 | 발행 | 받는 서비스 |
|---|---|---|---|
| `tspoon.user.events` (키: 회원 ID) | `EMAIL_VERIFICATION_REQUESTED` | user | notification (인증 메일) |
| | `PASSWORD_RESET_REQUESTED` | user | notification (재설정 메일) |
| | `PASSWORD_CHANGED` | user | notification (변경 알림 메일) |
| | `USER_PROFILE_UPDATED` | user | community · qna · education · event (작성자 이름) |
| | `USER_WITHDRAWN` | user | community · qna · education · event · notification (익명화·정리) |
| `tspoon.community.events` (키: 게시글 ID) | `COMMENT_CREATED` | community | notification |
| `tspoon.qna.events` (키: 질문 ID) | `ANSWER_CREATED` · `ANSWER_ACCEPTED` | qna | notification |
| `tspoon.event.events` (키: 행사 ID) | `EVENT_REMINDER` · `EVENT_CANCELLED` | event | notification (받을 회원 목록을 담아 보낸다) |

각 이벤트의 payload 형식은 [`event-schemas/{이벤트}.schema.json`](src/main/resources/event-schemas) (JSON Schema 2020-12).

## 계약을 지키는 방법

발행 측과 소비 측 테스트가 **같은 스키마**로 메시지를 검증한다(`testFixtures` 의 `EventContracts`).

- 발행 측: 실제로 낸 메시지(Kafka 레코드) 또는 outbox 에 기록한 payload 를 `EventContracts.assertConforms` / `assertPayloadConforms` 로 확인
- 소비 측: 보내는 픽스처를 `EventContracts.envelope(...)` 로 만든다 — 계약에 어긋난 픽스처는 만들어지지 않는다

그래서 발행 측이 필수 필드를 빼거나 형식을 바꾸면 **발행 측 테스트가 먼저 실패**한다. 소비 측 픽스처만 보던 때에는
운영에서 DLT 에 쌓이거나 알림이 조용히 빠진 뒤에야 알 수 있었다.

## 스키마를 바꾸는 규칙

소비자는 발행자보다 늦게 배포될 수 있으므로 **하위 호환 변경만** 한다.

| 변경 | 가능 여부 |
|---|---|
| 선택 필드 추가 | ✅ 그대로 추가 (`additionalProperties` 를 허용하므로 모르는 필드는 무시된다) |
| 필수 필드 추가 | ⚠️ 먼저 선택 필드로 추가 → 모든 발행자가 채우게 된 뒤 `required` 로 |
| 필드 삭제·이름 변경·타입 변경 | ❌ 새 이벤트(`..._V2` 등)를 만들고, 모든 소비자가 옮긴 뒤 옛 이벤트를 없앤다 |
| 이벤트 의미 변경 | ❌ 새 이벤트로 |

새 이벤트를 더할 때는 (1) 이벤트 이름 상수(`*EventTypes`) (2) 스키마 파일 (3) 발행 측 테스트의 계약 검증을 함께 추가한다.
