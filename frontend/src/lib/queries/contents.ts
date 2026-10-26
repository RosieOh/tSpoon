"use client";

import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, isApiError } from "@/lib/api";
import type { Content, ContentCategory, ContentInput, ContentSummary, ContentType, Page } from "@/lib/types";
import { contentQuery, contentsQuery } from "./keys";
import { nextPageParam, PAGE_SIZE } from "./paging";
import { useSession } from "./session";

export function useContents(filter: { category?: ContentCategory | null; type?: ContentType | null }, size?: number) {
  const q = contentsQuery(filter, size);
  return useInfiniteQuery({
    queryKey: q.key,
    queryFn: ({ pageParam, signal }) => api<Page<ContentSummary>>(q.path(pageParam), { signal }),
    initialPageParam: 0,
    getNextPageParam: nextPageParam,
  });
}

export function useContent(id: number) {
  const q = contentQuery(id);
  return useQuery({
    queryKey: q.key,
    queryFn: ({ signal }) => api<Content>(q.path, { signal }),
    staleTime: 60_000,
  });
}

export function useMyBookmarks() {
  const { user } = useSession();
  return useInfiniteQuery({
    queryKey: ["bookmarks", "list"],
    queryFn: ({ pageParam, signal }) =>
      api<Page<ContentSummary>>("/api/contents/bookmarks/me", { query: { page: pageParam, size: PAGE_SIZE }, signal }),
    initialPageParam: 0,
    getNextPageParam: nextPageParam,
    enabled: user !== null,
  });
}

// 상세 화면의 북마크 상태. 최근 100개 안에서 찾는다
function useBookmarkIds() {
  const { user } = useSession();
  return useQuery({
    queryKey: ["bookmarks", "ids"],
    queryFn: async () => {
      const page = await api<Page<ContentSummary>>("/api/contents/bookmarks/me", { query: { size: 100 } });
      return page.content.map((c) => c.id);
    },
    enabled: user !== null,
  });
}

export function useBookmark(contentId: number) {
  const client = useQueryClient();
  const ids = useBookmarkIds();
  const isBookmarked = ids.data?.includes(contentId) ?? false;

  const setLocal = (on: boolean) => {
    client.setQueryData<number[]>(["bookmarks", "ids"], (list = []) =>
      on ? [...new Set([contentId, ...list])] : list.filter((id) => id !== contentId),
    );
    client.setQueryData<Content>(["content", contentId], (c) =>
      c ? { ...c, bookmarkCount: Math.max(0, c.bookmarkCount + (on ? 1 : -1)) } : c,
    );
  };

  const mutation = useMutation({
    mutationFn: (on: boolean) =>
      api<void>(`/api/contents/${contentId}/bookmark`, { method: on ? "POST" : "DELETE" }),
    onMutate: (on) => setLocal(on),
    onError: (error, on) => {
      // 이미 담았거나 이미 뺀 상태였다면 목록만 서버와 맞춘다
      if ((on && isApiError(error, 409)) || (!on && isApiError(error, 404))) return;
      setLocal(!on);
    },
    onSettled: () => client.invalidateQueries({ queryKey: ["bookmarks"] }),
  });

  return {
    isBookmarked,
    ready: !ids.isPending,
    toggle: () => mutation.mutate(!isBookmarked),
    isPending: mutation.isPending,
    error: mutation.error,
  };
}

export function useSaveContent(id?: number) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: ContentInput) =>
      id
        ? api<Content>(`/api/contents/${id}`, { method: "PUT", body })
        : api<Content>("/api/contents", { method: "POST", body }),
    onSuccess: (content) => {
      client.setQueryData(["content", content.id], content);
      client.invalidateQueries({ queryKey: ["contents"] });
    },
  });
}

export function useDeleteContent() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api<void>(`/api/contents/${id}`, { method: "DELETE" }),
    onSuccess: (_, id) => {
      client.removeQueries({ queryKey: ["content", id] });
      client.invalidateQueries({ queryKey: ["contents"] });
      client.invalidateQueries({ queryKey: ["bookmarks"] });
    },
  });
}
