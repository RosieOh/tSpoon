import "server-only";
import { NextResponse, type NextRequest } from "next/server";
import type { UserRole } from "@/lib/types";

/*
 * 브라우저와 API Gateway 사이의 BFF.
 * - Access·Refresh Token 은 httpOnly 쿠키에만 두고 브라우저 JS 에는 내보내지 않는다.
 * - Refresh Token 은 한 번 쓰면 폐기되고, 이미 쓴 값이 다시 오면 user-service 가 계열 전체를 폐기한다(RTR).
 *   그래서 같은 Refresh Token 으로 동시에 들어온 재발급은 한 번만 보내고 결과를 나눠 쓴다.
 */

export const GATEWAY_URL = (process.env.TSPOON_GATEWAY_URL ?? "http://localhost:8080").replace(/\/$/, "");

const ACCESS_COOKIE = "tspoon_at";
const REFRESH_COOKIE = "tspoon_rt";
// Access Token 은 만료 30초 전에 쿠키가 먼저 사라지게 해서, 만료 직전 토큰으로 요청하지 않게 한다
const ACCESS_EARLY_EXPIRY_SECONDS = 30;
// 재발급 결과를 잠깐 기억해 둔다. 새 쿠키를 받기 전에 출발한 요청이 옛 Refresh Token 을 들고 와도 같은 결과를 준다
const RECENT_REFRESH_TTL_MS = 15_000;

export type Tokens = {
  accessToken: string;
  expiresIn: number;
  refreshToken: string;
  refreshExpiresIn: number;
  userId: number;
  email: string;
  role: UserRole;
};

type RefreshStore = {
  inflight: Map<string, Promise<Tokens | null>>;
  recent: Map<string, { tokens: Tokens; at: number }>;
};

// 라우트마다 모듈이 따로 로드돼도 한 프로세스에서는 같은 저장소를 쓰도록 globalThis 에 둔다
const store: RefreshStore = ((globalThis as { __tspoonRefresh?: RefreshStore }).__tspoonRefresh ??= {
  inflight: new Map(),
  recent: new Map(),
});

export function readSession(req: NextRequest) {
  return {
    accessToken: req.cookies.get(ACCESS_COOKIE)?.value,
    refreshToken: req.cookies.get(REFRESH_COOKIE)?.value,
  };
}

const cookieBase = {
  httpOnly: true,
  sameSite: "lax" as const,
  secure: process.env.NODE_ENV === "production",
  path: "/",
};

export function setSessionCookies(res: NextResponse, tokens: Tokens) {
  res.cookies.set(ACCESS_COOKIE, tokens.accessToken, {
    ...cookieBase,
    maxAge: Math.max(tokens.expiresIn - ACCESS_EARLY_EXPIRY_SECONDS, 1),
  });
  res.cookies.set(REFRESH_COOKIE, tokens.refreshToken, { ...cookieBase, maxAge: tokens.refreshExpiresIn });
}

export function clearSessionCookies(res: NextResponse) {
  res.cookies.set(ACCESS_COOKIE, "", { ...cookieBase, maxAge: 0 });
  res.cookies.set(REFRESH_COOKIE, "", { ...cookieBase, maxAge: 0 });
}

export async function refreshTokens(refreshToken: string): Promise<Tokens | null> {
  const recent = store.recent.get(refreshToken);
  if (recent && Date.now() - recent.at < RECENT_REFRESH_TTL_MS) return recent.tokens;

  const pending = store.inflight.get(refreshToken);
  if (pending) return pending;

  const request = (async () => {
    try {
      const res = await fetch(`${GATEWAY_URL}/api/users/token/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
        cache: "no-store",
      });
      if (!res.ok) return null;
      const tokens = (await res.json()) as Tokens;
      store.recent.set(refreshToken, { tokens, at: Date.now() });
      return tokens;
    } catch {
      return null;
    } finally {
      store.inflight.delete(refreshToken);
      for (const [key, value] of store.recent) {
        if (Date.now() - value.at > RECENT_REFRESH_TTL_MS) store.recent.delete(key);
      }
    }
  })();
  store.inflight.set(refreshToken, request);
  return request;
}

const FORWARDED_REQUEST_HEADERS = ["content-type", "accept", "accept-language"];
// fetch 가 압축을 풀어서 주므로 길이·인코딩 헤더는 다시 계산되게 버린다
const DROPPED_RESPONSE_HEADERS = ["content-encoding", "content-length", "transfer-encoding", "connection", "set-cookie"];

type ForwardResult = {
  response: Response;
  /** 재발급했다면 새 토큰. null 이면 재발급을 시도했지만 실패(세션 만료) */
  refreshed: Tokens | null | undefined;
};

/**
 * Gateway 로 요청을 보낸다. Access Token 이 없거나 401 이면 한 번 재발급하고 다시 보낸다.
 */
export async function callGateway(
  req: NextRequest,
  path: string,
  init: { method: string; body?: BodyInit | null; headers?: HeadersInit },
): Promise<ForwardResult> {
  const session = readSession(req);
  let refreshed: Tokens | null | undefined;
  let accessToken = session.accessToken;

  if (!accessToken && session.refreshToken) {
    refreshed = await refreshTokens(session.refreshToken);
    accessToken = refreshed?.accessToken;
  }

  const send = (token: string | undefined) => {
    const headers = new Headers(init.headers);
    for (const name of FORWARDED_REQUEST_HEADERS) {
      const value = req.headers.get(name);
      if (value && !headers.has(name)) headers.set(name, value);
    }
    if (token) headers.set("Authorization", `Bearer ${token}`);
    return fetch(`${GATEWAY_URL}${path}`, { method: init.method, headers, body: init.body, cache: "no-store" });
  };

  let response = await send(accessToken);
  if (response.status === 401 && session.refreshToken && refreshed === undefined) {
    refreshed = await refreshTokens(session.refreshToken);
    if (refreshed) response = await send(refreshed.accessToken);
  }
  return { response, refreshed };
}

export function toClientResponse(upstream: Response, refreshed: Tokens | null | undefined, hadSession: boolean) {
  const headers = new Headers();
  upstream.headers.forEach((value, key) => {
    if (!DROPPED_RESPONSE_HEADERS.includes(key.toLowerCase())) headers.set(key, value);
  });
  headers.set("Cache-Control", "no-store");
  const body = upstream.status === 204 || upstream.status === 304 ? null : upstream.body;
  const res = new NextResponse(body, { status: upstream.status, headers });
  if (refreshed) setSessionCookies(res, refreshed);
  else if (refreshed === null && hadSession) clearSessionCookies(res);
  return res;
}

export function problem(status: number, detail: string) {
  return NextResponse.json(
    { type: "about:blank", title: "Error", status, detail },
    { status, headers: { "Content-Type": "application/problem+json" } },
  );
}

export function gatewayUnavailable() {
  return problem(503, "서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.");
}
