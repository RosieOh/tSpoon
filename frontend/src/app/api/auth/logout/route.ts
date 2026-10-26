import { NextResponse, type NextRequest } from "next/server";
import { clearSessionCookies, GATEWAY_URL, readSession, refreshTokens } from "@/lib/server/gateway";

// 서버 쪽 폐기가 실패해도 쿠키는 지운다. 사용자는 언제든 로그아웃할 수 있어야 한다
export async function POST(req: NextRequest) {
  const session = readSession(req);
  let { accessToken, refreshToken } = session;

  try {
    if (!accessToken && refreshToken) {
      const tokens = await refreshTokens(refreshToken);
      accessToken = tokens?.accessToken;
      refreshToken = tokens?.refreshToken;
    }
    if (accessToken) {
      await fetch(`${GATEWAY_URL}/api/users/logout`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
        body: JSON.stringify({ refreshToken: refreshToken ?? null }),
        cache: "no-store",
      });
    }
  } catch {
    // 무시
  }

  const res = new NextResponse(null, { status: 204 });
  clearSessionCookies(res);
  return res;
}
