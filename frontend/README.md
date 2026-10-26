# 티스푼 웹 (frontend)

학부모·선생님·전문가가 아이 교육을 나누는 천재교육 티스푼의 웹 화면. Next.js 16 (App Router, Cache Components) · React 19 · Tailwind CSS 4 · TanStack Query.

## 실행

```bash
# 1) 백엔드 — 저장소 루트에서 (API Gateway: http://localhost:8080, Mailpit: http://localhost:8025)
./start-msa.sh

# 2) 데모 데이터 — 학부모·교사·전문가 계정과 글·콘텐츠·질문·행사를 넣는다
cd frontend
npm install
npm run seed

# 3) 웹
npm run dev        # http://localhost:3000
```

데모 계정(비밀번호 모두 `tspoon1234!`): `parent@tspoon.local`(학부모) · `teacher@tspoon.local`(선생님) · `expert@tspoon.local`(전문가).
관리자는 compose 에 넘긴 `ADMIN_EMAIL`·`ADMIN_PASSWORD`.

가입하면 인증 메일이 Mailpit 으로 간다. 메일 속 링크(`/verify-email?token=…`)를 열면 인증이 끝난다. 비밀번호 재설정 메일(`/reset-password?token=…`)도 같다.
링크 주소는 notification-service 의 `WEB_BASE_URL`(기본 `http://localhost:3000`)을 따른다.

| 환경 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `TSPOON_GATEWAY_URL` | `http://localhost:8080` | BFF 가 요청을 넘길 API Gateway 주소 (서버에서만 읽음) |
| `TSPOON_SITE_URL` | `http://localhost:3000` | 공유 미리보기·sitemap 의 절대 주소 |
| `TSPOON_PREFETCH` | (켜짐) | `off` 면 서버에서 공개 데이터를 미리 가져오지 않는다 |

## 구조

```
src/
  app/                 화면(라우트). 알림의 actionUrl(/boards/{id}, /questions/{id})과 같은 경로를 쓴다
    api/[...path]      BFF 프록시 — 브라우저의 /api/** 를 Gateway 로 넘기고 쿠키를 Bearer 로 바꿔 붙인다
    api/auth/*         로그인·로그아웃·세션·비밀번호 변경 — 토큰을 httpOnly 쿠키로만 주고받는다
  proxy.ts             로그인이 필요한 화면은 세션 쿠키가 없으면 /login 으로 (낙관적 확인)
  components/
    glass/LiquidGlass  떠 있는 층의 유리 — Chromium 에서는 SVG 변위 맵으로 가장자리 굴절까지
    shell/             탭바·사이드바·내비바·하단 CTA
    ui/                버튼·리스트 행·칩·시트·입력칸 등
    domain/            게시글 행·콘텐츠 타일·질문 행·행사 카드
  lib/
    queries/           서비스별 TanStack Query 훅
    server/gateway.ts  토큰 쿠키·재발급(single-flight)·Gateway 호출
    types.ts           백엔드 DTO 와 1:1 타입
    labels.ts          enum → 화면 이름·3D 아이콘·색
scripts/seed.mjs       데모 데이터
public/emoji/          3D 아이콘 (Microsoft Fluent Emoji, MIT)
```

### 인증

- Access·Refresh Token 은 브라우저 JS 에 내보내지 않는다. BFF 가 httpOnly 쿠키(`tspoon_at`, `tspoon_rt`)로 들고, Gateway 로 넘길 때 `Authorization: Bearer` 로 바꾼다.
- Refresh Token 은 한 번 쓰면 폐기되고 재사용하면 계열 전체가 폐기된다(RTR). 화면이 처음 뜰 때 여러 요청이 동시에 재발급을 시도하면
  로그아웃되므로, BFF 가 같은 Refresh Token 의 재발급을 한 번만 보내고 결과를 나눠 쓴다 (`lib/server/gateway.ts`).
- 로그인 실패는 `code` 로 나눠 보여준다: `EMAIL_NOT_VERIFIED`(인증 메일 다시 받기), `LOGIN_LOCKED`·`RATE_LIMITED`(429, `Retry-After`).

### 디자인

- 브랜드는 천재교육 CI 컬러시스템을 따른다: CHUNJAE GREEN `#009944`(메인·버튼), SKY BLUE `#008CCF`(정보 배지), BLUE `#265C99`, BLACK(선택된 칩).
  흰 바탕 위 작은 초록 글씨는 대비 4.5:1 을 넘는 `--primary-ink`(`#00873D`)를 쓴다.
- 콘텐츠·행사 그림은 천재 교과서 표지 구성(원색 바탕 · 흰 원 안의 캐릭터 · 굵은 과목명)을 따른다 — `components/domain/CoverArt`.
  표지 위 글자색은 색 대비로 고른다(`coverInk`).
- 티스푼 마크(`components/brand/BrandMark`, `app/icon.svg`)는 CI 그린과 천재 노랑으로 만든 자체 마크다. 천재교육 로고·해법 심볼은 상표라 넣지 않았다.
- 콘텐츠 층은 회색 바탕 위 흰 블록과 또렷한 리스트. 탭바·내비바 버튼·시트·토스트처럼 떠 있는 층만 Liquid Glass.
- 색·글자 크기·둥글기는 `app/globals.css` 의 토큰을 쓴다. 다크 모드는 시스템 설정을 따르고, 투명도 줄이기·동작 줄이기 설정도 따른다.
- Cache Components 는 지나간 화면을 Activity 로 보존한다. 새로 쓰는 화면은 `shell/Fresh` 로 감싸 들어올 때마다 빈 폼에서 시작한다.

### 성능

- **서버에서 미리 가져오기**: 홈·목록·상세는 정적 뼈대를 바로 보내고, 공개 데이터를 담은 화면을 같은 응답 안에서 스트리밍한다
  (`lib/server/prefetch.ts` → `HydrationBoundary`). 브라우저 훅은 같은 쿼리 키(`lib/queries/keys.ts`)로 그 데이터를 이어받는다.
  - 서버는 인증 없이 공개 API 만 부르고, 실패하거나 2.5초 넘게 걸리면 넘긴다(브라우저가 이어서 가져옴).
  - 같은 요청 안에서는 한 번만 부른다(React `cache`). 상세의 `generateMetadata` 와 본문이 같은 호출을 써서 조회수는 한 번만 오른다.
  - Suspense fallback 에는 쿼리 훅을 부르는 컴포넌트를 두지 않는다. 같은 QueryClient 에 빈 쿼리가 먼저 생기면 서버 HTML 에 데이터가 빠진다.
- **글꼴**: Pretendard 동적 서브셋을 같은 출처에서 서빙한다. 처음엔 시스템 글꼴로 그리고, 화면 글자에 필요한 서브셋이 모두 오면
  한 번에 바꾼다(`layout.tsx` 의 `FONT_LOADER`) — 서브셋이 올 때마다 다시 그리던 비용을 줄인다.
- 첫 화면의 큰 그림(히어로·첫 줄 타일)은 지연 로딩하지 않고 먼저 받는다. 화면 밖 목록 항목은 `content-visibility: auto`.
- 상세 화면의 제목·설명: JS 를 실행하는 수집기(구글)에는 본문으로 스트리밍하고, 네이버(Yeti)·다음·카카오톡 미리보기 등에는
  `<head>` 에 담아 보낸다(`next.config.ts` 의 `htmlLimitedBots`).

## 확인

```bash
npm run typecheck
npm run lint
```
