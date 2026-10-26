import "server-only";

/** URL 쿼리 값 중 허용된 값만 — 클라이언트 useQueryParam 과 같은 규칙 */
export function pickParam<T extends string>(value: string | string[] | undefined, allowed: readonly T[]): T | null {
  const raw = Array.isArray(value) ? value[0] : value;
  return raw && (allowed as readonly string[]).includes(raw) ? (raw as T) : null;
}

/** 경로의 숫자 id. 숫자가 아니면 null */
export function toId(value: string): number | null {
  const id = Number(value);
  return Number.isInteger(id) && id > 0 ? id : null;
}
