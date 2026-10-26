import { NextResponse, type NextRequest } from "next/server";
import { GATEWAY_URL, gatewayUnavailable, setSessionCookies, type Tokens } from "@/lib/server/gateway";

export async function POST(req: NextRequest) {
  const body = await req.text();
  let upstream: Response;
  try {
    upstream = await fetch(`${GATEWAY_URL}/api/users/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body,
      cache: "no-store",
    });
  } catch {
    return gatewayUnavailable();
  }

  if (!upstream.ok) {
    const headers = new Headers({ "Content-Type": upstream.headers.get("content-type") ?? "application/problem+json" });
    // 로그인 잠금·횟수 제한(429)이면 언제 다시 시도할 수 있는지도 넘긴다
    const retryAfter = upstream.headers.get("retry-after");
    if (retryAfter) headers.set("Retry-After", retryAfter);
    return new NextResponse(upstream.body, { status: upstream.status, headers });
  }

  const tokens = (await upstream.json()) as Tokens;
  const res = NextResponse.json({ userId: tokens.userId, email: tokens.email, role: tokens.role });
  setSessionCookies(res, tokens);
  return res;
}
