"use client";

import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { EventInput, EventType, Page, Registration, TspoonEvent } from "@/lib/types";
import { eventQuery, eventsQuery } from "./keys";
import { nextPageParam, PAGE_SIZE } from "./paging";
import { useSession } from "./session";

export function useEvents(type?: EventType | null, size = PAGE_SIZE) {
  const q = eventsQuery(type, size);
  return useInfiniteQuery({
    queryKey: q.key,
    queryFn: ({ pageParam, signal }) => api<Page<TspoonEvent>>(q.path(pageParam), { signal }),
    initialPageParam: 0,
    getNextPageParam: nextPageParam,
  });
}

export function useEvent(id: number) {
  const q = eventQuery(id);
  return useQuery({
    queryKey: q.key,
    queryFn: ({ signal }) => api<TspoonEvent>(q.path, { signal }),
  });
}

/** 내 신청 내역(최근 100건) — 상세 화면에서 신청 여부를 판단할 때도 쓴다 */
export function useMyRegistrations() {
  const { user } = useSession();
  return useQuery({
    queryKey: ["registrations", "me"],
    queryFn: async () =>
      (await api<Page<Registration>>("/api/events/registrations/me", { query: { size: 100 } })).content,
    enabled: user !== null,
  });
}

export function useEventRegistrations(id: number, enabled: boolean) {
  return useQuery({
    queryKey: ["registrations", "event", id],
    queryFn: () => api<Registration[]>(`/api/events/${id}/registrations`),
    enabled,
  });
}

function useRefreshEvent(id: number) {
  const client = useQueryClient();
  return () => {
    client.invalidateQueries({ queryKey: ["event", id] });
    client.invalidateQueries({ queryKey: ["events"] });
    client.invalidateQueries({ queryKey: ["registrations"] });
  };
}

export function useRegister(id: number) {
  const refresh = useRefreshEvent(id);
  return useMutation({
    mutationFn: (body: { notes?: string; contactInfo?: string }) =>
      api<Registration>(`/api/events/${id}/registrations`, { method: "POST", body }),
    onSuccess: refresh,
  });
}

export function useCancelRegistration(id: number) {
  const refresh = useRefreshEvent(id);
  return useMutation({
    mutationFn: () => api<void>(`/api/events/${id}/registrations/me`, { method: "DELETE" }),
    onSuccess: refresh,
  });
}

export function useCancelEvent(id: number) {
  const refresh = useRefreshEvent(id);
  return useMutation({
    mutationFn: () => api<void>(`/api/events/${id}/cancel`, { method: "POST" }),
    onSuccess: refresh,
  });
}

export function useSaveEvent(id?: number) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: EventInput) =>
      id
        ? api<TspoonEvent>(`/api/events/${id}`, { method: "PUT", body })
        : api<TspoonEvent>("/api/events", { method: "POST", body }),
    onSuccess: (event) => {
      client.setQueryData(["event", event.id], event);
      client.invalidateQueries({ queryKey: ["events"] });
    },
  });
}

export function useMyRegistrationList() {
  const { user } = useSession();
  return useInfiniteQuery({
    queryKey: ["registrations", "me", "list"],
    queryFn: ({ pageParam, signal }) =>
      api<Page<Registration>>("/api/events/registrations/me", { query: { page: pageParam, size: PAGE_SIZE }, signal }),
    initialPageParam: 0,
    getNextPageParam: nextPageParam,
    enabled: user !== null,
  });
}
