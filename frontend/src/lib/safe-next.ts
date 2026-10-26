/** 로그인 뒤 돌아갈 주소. 앱 안 경로만 허용해 열린 리다이렉트를 막는다 */
export function safeNext(next: string | null | undefined, fallback = "/") {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) return fallback;
  return next;
}

/** 초 → "3분", "40초" */
export function waitText(seconds: number) {
  if (seconds >= 60) return `${Math.ceil(seconds / 60)}분`;
  return `${seconds}초`;
}
