import { NextResponse, type NextRequest } from "next/server";
import { callGateway, gatewayUnavailable, readSession, setSessionCookies, toClientResponse, type Tokens } from "@/lib/server/gateway";

// 비밀번호 변경. 성공하면 다른 기기의 로그인은 끊기고 새 토큰이 오므로, 그 토큰을 쿠키로만 받는다
export async function PUT(req: NextRequest) {
  const body = await req.text();
  try {
    const { response, refreshed } = await callGateway(req, "/api/users/password", {
      method: "PUT",
      body,
      headers: { "Content-Type": "application/json" },
    });
    if (!response.ok) return toClientResponse(response, refreshed, Boolean(readSession(req).refreshToken));

    const tokens = (await response.json()) as Tokens;
    const res = new NextResponse(null, { status: 204 });
    setSessionCookies(res, tokens);
    return res;
  } catch {
    return gatewayUnavailable();
  }
}
