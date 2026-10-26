import { NextResponse, type NextRequest } from "next/server";

// 로그인해야 쓸 수 있는 화면은 세션 쿠키가 없으면 로그인으로 보낸다.
// 쿠키가 있는지만 보는 낙관적 확인이고, 실제 권한은 각 서비스가 판단한다.
const AUTH_PAGES = ["/login", "/signup"];

export function proxy(req: NextRequest) {
  const hasSession = req.cookies.has("tspoon_rt") || req.cookies.has("tspoon_at");
  const { pathname, search } = req.nextUrl;

  if (AUTH_PAGES.includes(pathname)) {
    return hasSession ? NextResponse.redirect(new URL("/", req.url)) : NextResponse.next();
  }

  if (!hasSession) {
    const login = new URL("/login", req.url);
    login.searchParams.set("next", pathname + search);
    return NextResponse.redirect(login);
  }
  return NextResponse.next();
}

export const config = {
  matcher: [
    "/login",
    "/signup",
    "/me/:path*",
    "/notifications",
    "/admin/:path*",
    "/boards/new",
    "/boards/:id/edit",
    "/contents/new",
    "/contents/:id/edit",
    "/questions/new",
    "/questions/:id/edit",
    "/events/new",
    "/events/:id/edit",
  ],
};
