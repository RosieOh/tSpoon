"use client";

import { useCallback, useSyncExternalStore } from "react";

/*
 * 브라우저에만 남기는 작은 id 집합.
 * 게시글 API 는 "내가 좋아요를 눌렀는지"를 내려 주지 않아서, 누른 기록을 여기 두고
 * 서버가 409(이미 누름)·404(누른 적 없음)로 답하면 그에 맞춰 바로잡는다.
 */
const listeners = new Set<() => void>();
const cache = new Map<string, ReadonlySet<number>>();
const EMPTY: ReadonlySet<number> = new Set();

function read(key: string): ReadonlySet<number> {
  const hit = cache.get(key);
  if (hit) return hit;
  let value: ReadonlySet<number> = EMPTY;
  try {
    const raw = localStorage.getItem(key);
    if (raw) value = new Set(JSON.parse(raw) as number[]);
  } catch {
    // 저장소를 못 쓰는 환경이면 빈 집합
  }
  cache.set(key, value);
  return value;
}

function write(key: string, next: ReadonlySet<number>) {
  cache.set(key, next);
  try {
    localStorage.setItem(key, JSON.stringify([...next]));
  } catch {
    // 저장 실패는 무시한다. 이 값은 힌트일 뿐이다
  }
  listeners.forEach((l) => l());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function useLocalIdSet(key: string | null) {
  const set = useSyncExternalStore(
    subscribe,
    () => (key ? read(key) : EMPTY),
    () => EMPTY,
  );
  const toggle = useCallback(
    (id: number, on: boolean) => {
      if (!key) return;
      const next = new Set(read(key));
      if (on) next.add(id);
      else next.delete(id);
      write(key, next);
    },
    [key],
  );
  return [set, toggle] as const;
}
