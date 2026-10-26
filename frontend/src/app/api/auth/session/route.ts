import { NextResponse, type NextRequest } from "next/server";
import { callGateway, clearSessionCookies, readSession, setSessionCookies } from "@/lib/server/gateway";
import type { User } from "@/lib/types";

// 지금 로그인한 사용자. 로그인하지 않았거나 세션이 끝났으면 { user: null }
export async function GET(req: NextRequest) {
  const session = readSession(req);
  if (!session.accessToken && !session.refreshToken) return NextResponse.json({ user: null });

  try {
    const { response, refreshed } = await callGateway(req, "/api/users/profile", { method: "GET" });
    const user = response.ok ? ((await response.json()) as User) : null;
    const res = NextResponse.json({ user }, { headers: { "Cache-Control": "no-store" } });
    // 재발급으로 옛 Refresh Token 은 이미 폐기됐으니, 프로필 조회가 실패해도 새 토큰은 반드시 내려 준다
    if (refreshed) setSessionCookies(res, refreshed);
    else if (refreshed === null || response.status === 401) clearSessionCookies(res);
    return res;
  } catch {
    return NextResponse.json({ user: null, offline: true }, { status: 503 });
  }
}
