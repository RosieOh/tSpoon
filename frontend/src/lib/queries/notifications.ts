"use client";

import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { Notification, Page } from "@/lib/types";
import { nextPageParam, PAGE_SIZE } from "./paging";
import { useSession } from "./session";

export function useUnreadCount() {
  const { user } = useSession();
  return useQuery({
    queryKey: ["notifications", "unread-count"],
    queryFn: async () => (await api<{ count: number }>("/api/notifications/unread-count")).count,
    enabled: user !== null,
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  });
}

export function useNotifications() {
  return useInfiniteQuery({
    queryKey: ["notifications", "list"],
    queryFn: ({ pageParam, signal }) =>
      api<Page<Notification>>("/api/notifications", { query: { page: pageParam, size: PAGE_SIZE }, signal }),
    initialPageParam: 0,
    getNextPageParam: nextPageParam,
  });
}

export function useMarkRead() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api<Notification>(`/api/notifications/${id}/read`, { method: "PATCH" }),
    onSuccess: () => client.invalidateQueries({ queryKey: ["notifications"] }),
  });
}

export function useMarkAllRead() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => api<{ updated: number }>("/api/notifications/read-all", { method: "PATCH" }),
    onSuccess: () => client.invalidateQueries({ queryKey: ["notifications"] }),
  });
}
