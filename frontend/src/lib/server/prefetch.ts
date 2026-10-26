import "server-only";
import { dehydrate, QueryClient, type DehydratedState, type QueryKey } from "@tanstack/react-query";
import { connection } from "next/server";
import { cache } from "react";
import type { Page } from "@/lib/types";
import { GATEWAY_URL } from "./gateway";

/*
 * 서버에서 공개 데이터를 미리 가져와 첫 HTML 에 담는다.
 * 브라우저가 JS 를 받고 → 세션을 확인하고 → 데이터를 요청한 뒤에야 그리던 것을, 응답 스트림 안에서 바로 그린다.
 * - 인증 없이 Gateway 의 공개 API 만 부른다. 실패하거나 느리면 넘기고, 브라우저 훅이 평소처럼 가져온다.
 * - 같은 요청 안의 같은 경로는 한 번만 부른다(React cache). 페이지와 generateMetadata 가 함께 써도
 *   상세 조회수(조회 API 가 올림)가 두 번 오르지 않는다.
 */

const TIMEOUT_MS = 2500;

export const getPublic = cache(async function getPublic<T>(path: string): Promise<T | null> {
  // 요청마다 달라지는 데이터라 프리렌더(빌드)에서는 부르지 않는다
  await connection();
  try {
    const res = await fetch(`${GATEWAY_URL}${path}`, {
      headers: { Accept: "application/json" },
      cache: "no-store",
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    return res.ok ? ((await res.json()) as T) : null;
  } catch {
    return null;
  }
}) as <T>(path: string) => Promise<T | null>;

type Seed = (client: QueryClient) => Promise<void>;

/** 무한 목록의 첫 쪽 */
export function seedFirstPage<T>(query: { key: QueryKey; path: (page: number) => string }): Seed {
  return async (client) => {
    const page = await getPublic<Page<T>>(query.path(0));
    if (page) client.setQueryData(query.key, { pages: [page], pageParams: [0] });
  };
}

/** 단건 */
export function seedOne<T>(query: { key: QueryKey; path: string }): Seed {
  return async (client) => {
    const data = await getPublic<T>(query.path);
    if (data) client.setQueryData(query.key, data);
  };
}

// TSPOON_PREFETCH=off 면 서버에서 미리 가져오지 않는다 (성능 비교·장애 시 끄기용)
const PREFETCH_ENABLED = process.env.TSPOON_PREFETCH !== "off";

export async function prefetch(seeds: Seed[]): Promise<DehydratedState> {
  const client = new QueryClient();
  if (PREFETCH_ENABLED) await Promise.all(seeds.map((seed) => seed(client)));
  else await connection();
  return dehydrate(client);
}
