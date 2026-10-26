import type { ProblemDetail } from "./types";

// 같은 출처의 BFF(/api/**)만 부른다. 인증은 httpOnly 쿠키가 알아서 실어 나른다.

export class ApiError extends Error {
  readonly status: number;
  readonly errors: Record<string, string>;
  readonly code: string | undefined;
  /** 429 일 때 다시 시도할 수 있기까지 남은 초 */
  readonly retryAfter: number | undefined;

  constructor(problem: ProblemDetail, retryAfter?: number) {
    super(problem.detail ?? "요청을 처리하지 못했어요.");
    this.status = problem.status;
    this.errors = problem.errors ?? {};
    this.code = problem.code;
    this.retryAfter = retryAfter;
  }

  /** 필드 오류가 있으면 첫 번째, 없으면 detail */
  get userMessage() {
    return Object.values(this.errors)[0] ?? this.message;
  }
}

type Query = Record<string, string | number | boolean | null | undefined>;

export function withQuery(path: string, query?: Query) {
  if (!query) return path;
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== "") params.set(key, String(value));
  }
  const qs = params.toString();
  return qs ? `${path}?${qs}` : path;
}

export async function api<T>(
  path: string,
  init: { method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE"; body?: unknown; query?: Query; signal?: AbortSignal } = {},
): Promise<T> {
  let res: Response;
  try {
    res = await fetch(withQuery(path, init.query), {
      method: init.method ?? "GET",
      headers: init.body === undefined ? undefined : { "Content-Type": "application/json" },
      body: init.body === undefined ? undefined : JSON.stringify(init.body),
      credentials: "same-origin",
      signal: init.signal,
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw new ApiError({ status: 0, detail: "인터넷 연결을 확인해 주세요." });
  }

  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? safeJson(text) : null;
  if (!res.ok) {
    const problem = (data && typeof data === "object" ? data : {}) as Partial<ProblemDetail>;
    const retryAfter = Number(res.headers.get("Retry-After")) || undefined;
    throw new ApiError({ ...problem, status: res.status, detail: problem.detail ?? fallbackMessage(res.status) }, retryAfter);
  }
  return data as T;
}

function safeJson(text: string) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function fallbackMessage(status: number) {
  if (status === 401) return "로그인이 필요해요.";
  if (status === 403) return "권한이 없어요.";
  if (status === 404) return "찾을 수 없어요.";
  if (status === 429) return "요청이 너무 많아요. 잠시 후 다시 시도해 주세요.";
  if (status >= 500) return "서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.";
  return "요청을 처리하지 못했어요.";
}

export function isApiError(e: unknown, status?: number): e is ApiError {
  return e instanceof ApiError && (status === undefined || e.status === status);
}
