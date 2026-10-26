"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useIsClient } from "@/lib/hooks";
import type { Child, ChildInput, SessionUser, User } from "@/lib/types";

export const sessionKey = ["session"] as const;

export function useSession() {
  const query = useQuery({
    queryKey: sessionKey,
    queryFn: async () => (await api<{ user: User | null }>("/api/auth/session")).user,
    staleTime: 5 * 60_000,
    retry: false,
  });
  // 로그인하지 않았으면 세션 응답이 아주 빨리 와서, 늦게 하이드레이트되는 조각이 서버 HTML(로딩 상태)과 다르게 그릴 수 있다.
  // 하이드레이션이 끝날 때까지는 서버와 같은 "확인 중" 상태로 둔다
  const hydrated = useIsClient();
  return {
    user: hydrated ? (query.data ?? null) : null,
    isLoading: !hydrated || query.isPending,
  };
}

export function useLogin() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: { email: string; password: string }) =>
      api<SessionUser>("/api/auth/login", { method: "POST", body }),
    // 다른 사람으로 로그인했을 수 있으니 사용자별 캐시를 모두 비우고 다시 받는다
    onSuccess: () => client.resetQueries(),
  });
}

export function useSignup() {
  return useMutation({
    mutationFn: (body: { email: string; password: string; name: string; phone?: string }) =>
      api<User>("/api/users/register", { method: "POST", body }),
  });
}

export function useLogout() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => api<void>("/api/auth/logout", { method: "POST" }),
    onSettled: () => {
      client.clear();
      client.setQueryData(sessionKey, null);
    },
  });
}

export function useUpdateProfile() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: { name: string; phone?: string | null; address?: string | null }) =>
      api<User>("/api/users/profile", { method: "PUT", body }),
    onSuccess: (user) => client.setQueryData(sessionKey, user),
  });
}

export function useChildren(enabled: boolean) {
  return useQuery({
    queryKey: ["children"],
    queryFn: () => api<Child[]>("/api/users/children"),
    enabled,
    staleTime: 10 * 60_000,
  });
}

export function useSaveChild(id?: number) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: ChildInput) =>
      id
        ? api<Child>(`/api/users/children/${id}`, { method: "PUT", body })
        : api<Child>("/api/users/children", { method: "POST", body }),
    onSuccess: () => client.invalidateQueries({ queryKey: ["children"] }),
  });
}

export function useDeleteChild() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api<void>(`/api/users/children/${id}`, { method: "DELETE" }),
    onSuccess: () => client.invalidateQueries({ queryKey: ["children"] }),
  });
}

// 이메일 인증 — 가입 후 받은 메일의 링크로 확인한다
export function useConfirmEmail() {
  return useMutation({
    mutationFn: (token: string) =>
      api<void>("/api/users/email-verification/confirm", { method: "POST", body: { token } }),
  });
}

/** 가입 여부와 상관없이 항상 성공한다(가입한 이메일인지 드러내지 않음) */
export function useResendVerification() {
  return useMutation({
    mutationFn: (email: string) =>
      api<void>("/api/users/email-verification/resend", { method: "POST", body: { email } }),
  });
}

/** 가입 여부와 상관없이 항상 성공한다 */
export function useRequestPasswordReset() {
  return useMutation({
    mutationFn: (email: string) => api<void>("/api/users/password-reset/request", { method: "POST", body: { email } }),
  });
}

export function useResetPassword() {
  return useMutation({
    mutationFn: (body: { token: string; newPassword: string }) =>
      api<void>("/api/users/password-reset/confirm", { method: "POST", body }),
  });
}

/** 다른 기기의 로그인은 끊기고, 이 기기는 BFF 가 새 토큰을 쿠키로 받아 계속 쓴다 */
export function useChangePassword() {
  return useMutation({
    mutationFn: (body: { currentPassword: string; newPassword: string }) =>
      api<void>("/api/auth/password", { method: "PUT", body }),
  });
}

export const canCreateContent = (user: User | null) =>
  user !== null && (user.role === "TEACHER" || user.role === "EXPERT" || user.role === "ADMIN");
