"use client";

import clsx from "clsx";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, type ReactNode } from "react";
import { SkeletonBlock, SkeletonRows } from "@/components/ui/States";
import { AccountActions, BackButton, LargeTitle, NavBar } from "./NavBar";

/** 탭 화면 머리 — 떠 있는 계정 버튼 + 큰 제목 */
export function TabHeader({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <>
      <NavBar right={<AccountActions />} title={title} />
      <LargeTitle title={title}>{children}</LargeTitle>
    </>
  );
}

/** 화면 아래에 붙는 큰 버튼 자리 — 토스식 "한 화면, 한 행동" */
export function BottomCTA({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={clsx(
        "column-fixed bottom-0 z-30 px-5 pt-8 pb-[max(16px,env(safe-area-inset-bottom))]",
        // 위쪽 28px 만 흐려지고 그 아래는 불투명 — 상태 문구가 뒤 글자와 겹치지 않게
        "bg-[linear-gradient(to_bottom,transparent,var(--bg)_28px)]",
        className,
      )}
    >
      {children}
    </div>
  );
}

/** 목록 화면 로딩 뼈대 */
export function ListScreenSkeleton({ title }: { title: string }) {
  return (
    <>
      <NavBar right={<AccountActions />} />
      <LargeTitle title={title} />
      <div className="flex gap-2 px-5 py-1">
        {Array.from({ length: 4 }, (_, i) => (
          <SkeletonBlock key={i} className="h-9 w-16 rounded-full" />
        ))}
      </div>
      <div className="mx-3 mt-4 rounded-section bg-surface">
        <SkeletonRows count={6} icon={false} />
      </div>
    </>
  );
}

/** 상세 화면 로딩 뼈대 */
export function DetailScreenSkeleton({ fallback }: { fallback: string }) {
  return (
    <>
      <NavBar left={<BackButton fallback={fallback} />} />
      <div className="mx-3 space-y-4 rounded-section bg-surface px-5 py-6">
        <SkeletonBlock className="h-6 w-16" />
        <SkeletonBlock className="h-8 w-4/5" />
        <SkeletonBlock className="h-10 w-40" />
        <SkeletonBlock className="h-40 w-full" />
      </div>
    </>
  );
}

/** URL 쿼리에 담는 필터 상태 — 새로고침·공유·뒤로 가기에도 유지된다 */
export function useQueryParam<T extends string>(name: string, allowed: readonly T[]) {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const raw = params.get(name);
  const value = raw && (allowed as readonly string[]).includes(raw) ? (raw as T) : null;

  const set = useCallback(
    (next: T | null) => {
      const copy = new URLSearchParams(params.toString());
      if (next) copy.set(name, next);
      else copy.delete(name);
      const qs = copy.toString();
      router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
    },
    [name, params, pathname, router],
  );

  return [value, set] as const;
}
