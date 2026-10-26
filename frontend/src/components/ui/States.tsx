"use client";

import clsx from "clsx";
import { useEffect, useRef, type ReactNode } from "react";
import { ApiError } from "@/lib/api";
import { Button } from "./Button";
import { Emoji } from "./Emoji";
import { Spinner } from "./Spinner";

export function EmptyState({
  emoji,
  title,
  description,
  action,
  className,
}: {
  emoji: string;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={clsx("flex flex-col items-center px-8 py-14 text-center", className)}>
      <Emoji name={emoji} size={72} />
      <p className="mt-4 text-t4 font-semibold text-ink">{title}</p>
      {description && <p className="mt-1 text-t6 text-ink-3">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function ErrorState({ error, onRetry, className }: { error: unknown; onRetry?: () => void; className?: string }) {
  const notFound = error instanceof ApiError && error.status === 404;
  const message = error instanceof ApiError ? error.userMessage : "잠시 후 다시 시도해 주세요.";
  return (
    <EmptyState
      className={className}
      emoji={notFound ? "search" : "thinking"}
      title={notFound ? "찾을 수 없어요" : "불러오지 못했어요"}
      description={notFound ? "삭제됐거나 주소가 바뀌었을 수 있어요." : message}
      action={
        !notFound && onRetry ? (
          <Button variant="neutral" size="md" onClick={onRetry}>
            다시 불러오기
          </Button>
        ) : undefined
      }
    />
  );
}

export function SkeletonRows({ count = 4, icon = true }: { count?: number; icon?: boolean }) {
  return (
    <div aria-hidden className="py-1">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="flex items-center gap-3.5 px-5 py-3.5">
          {icon && <div className="skeleton size-11 shrink-0 rounded-[14px]" />}
          <div className="flex-1 space-y-2">
            <div className="skeleton h-4 w-3/4" />
            <div className="skeleton h-3.5 w-1/2" />
          </div>
        </div>
      ))}
    </div>
  );
}

export function SkeletonBlock({ className }: { className?: string }) {
  return <div aria-hidden className={clsx("skeleton", className)} />;
}

/** 목록 끝에 닿으면 다음 쪽을 불러온다 */
export function LoadMore({
  hasNextPage,
  isFetchingNextPage,
  fetchNextPage,
}: {
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  fetchNextPage: () => unknown;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el || !hasNextPage) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !isFetchingNextPage) fetchNextPage();
      },
      { rootMargin: "400px 0px" },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  if (!hasNextPage) return null;
  return (
    <div ref={ref} className="flex justify-center py-6 text-ink-4">
      {isFetchingNextPage && <Spinner />}
    </div>
  );
}
