import type { NextRequest } from "next/server";
import { callGateway, gatewayUnavailable, problem, readSession, toClientResponse } from "@/lib/server/gateway";

// 브라우저의 /api/** 를 Gateway 의 /api/** 로 그대로 넘긴다. 인증 쿠키를 Bearer 헤더로 바꿔 붙인다.
// 토큰을 주고받는 엔드포인트는 /api/auth/* 로만 쓰게 막아, 토큰이 브라우저 JS 에 닿지 않게 한다.
const TOKEN_ENDPOINTS = new Set(["users/login", "users/token/refresh", "users/logout", "users/password"]);

async function handle(req: NextRequest, ctx: RouteContext<"/api/[...path]">) {
  const { path } = await ctx.params;
  const joined = path.join("/");
  if (TOKEN_ENDPOINTS.has(joined)) return problem(404, "찾을 수 없는 경로예요.");

  const hasBody = req.method !== "GET" && req.method !== "HEAD";
  const body = hasBody ? await req.arrayBuffer() : undefined;
  try {
    const { response, refreshed } = await callGateway(req, `/api/${joined}${req.nextUrl.search}`, {
      method: req.method,
      body: body && body.byteLength > 0 ? body : undefined,
    });
    return toClientResponse(response, refreshed, Boolean(readSession(req).refreshToken));
  } catch {
    return gatewayUnavailable();
  }
}

export { handle as GET, handle as POST, handle as PUT, handle as PATCH, handle as DELETE };
