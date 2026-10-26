"use client";

import { useSyncExternalStore } from "react";

const noopSubscribe = () => () => {};

/** 하이드레이션이 끝난 브라우저에서만 true — 포털처럼 서버에서 그릴 수 없는 것에 쓴다 */
export function useIsClient() {
  return useSyncExternalStore(
    noopSubscribe,
    () => true,
    () => false,
  );
}

function subscribeScroll(callback: () => void) {
  window.addEventListener("scroll", callback, { passive: true });
  return () => window.removeEventListener("scroll", callback);
}

/** 문서가 threshold(px) 넘게 스크롤됐는지 */
export function useScrolled(threshold = 8) {
  return useSyncExternalStore(
    subscribeScroll,
    () => window.scrollY > threshold,
    () => false,
  );
}
