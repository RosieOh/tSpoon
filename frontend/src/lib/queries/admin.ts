"use client";

import { useInfiniteQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { Page, User, UserRole, UserStatus } from "@/lib/types";
import { nextPageParam, PAGE_SIZE } from "./paging";

export function useAdminUsers() {
  return useInfiniteQuery({
    queryKey: ["admin", "users"],
    queryFn: ({ pageParam, signal }) =>
      api<Page<User>>("/api/users", { query: { page: pageParam, size: PAGE_SIZE }, signal }),
    initialPageParam: 0,
    getNextPageParam: nextPageParam,
  });
}

export function useChangeUser() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, role, status }: { id: number; role?: UserRole; status?: UserStatus }) =>
      role
        ? api<User>(`/api/admin/users/${id}/role`, { method: "PATCH", body: { role } })
        : api<User>(`/api/admin/users/${id}/status`, { method: "PATCH", body: { status } }),
    onSuccess: () => client.invalidateQueries({ queryKey: ["admin", "users"] }),
  });
}
