import type { InfiniteData } from "@tanstack/react-query";
import type { Page } from "@/lib/types";

export const PAGE_SIZE = 20;

export function nextPageParam<T>(last: Page<T>) {
  const { number, totalPages } = last.page;
  return number + 1 < totalPages ? number + 1 : undefined;
}

export function flatten<T>(data: InfiniteData<Page<T>> | undefined): T[] {
  return data?.pages.flatMap((p) => p.content) ?? [];
}

export function totalOf<T>(data: InfiniteData<Page<T>> | undefined): number | undefined {
  return data?.pages[0]?.page.totalElements;
}
