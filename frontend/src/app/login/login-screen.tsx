"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { BrandMark } from "@/components/brand/BrandMark";
import { useOverlay } from "@/components/overlays/Overlays";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { BottomCTA } from "@/components/shell/Screen";
import { Button } from "@/components/ui/Button";
import { Emoji } from "@/components/ui/Emoji";
import { TextField } from "@/components/ui/Field";
import { ApiError, isApiError } from "@/lib/api";
import { useLogin, useResendVerification } from "@/lib/queries/session";
import { safeNext, waitText } from "@/lib/safe-next";

export function LoginScreen() {
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const { toast } = useOverlay();
  const login = useLogin();
  const resend = useResendVerification();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<ApiError | null>(null);

  const valid = /\S+@\S+\.\S+/.test(email) && password.length > 0;

  const submit = () => {
    if (!valid || login.isPending) return;
    setError(null);
    login.mutate(
      { email: email.trim(), password },
      {
        onSuccess: () => {
          setPassword("");
          router.replace(next);
        },
        onError: (e) => {
          if (isApiError(e)) setError(e);
          else toast("로그인하지 못했어요", "error");
        },
      },
    );
  };

  const unverified = error?.code === "EMAIL_NOT_VERIFIED";
  const locked = error?.status === 429;
  const wrongCredentials = error?.status === 401;

  return (
    <>
      <NavBar left={<BackButton close fallback="/" />} />
      <div className="px-5 pt-2">
        <BrandMark size={52} className="mb-5" />
        <h1 className="text-t1 text-ink">
          티스푼 계정으로
          <br />
          로그인해 주세요
        </h1>

        <form
          id="login-form"
          className="mt-9 space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            submit();
          }}
        >
          <TextField
            onBg
            label="이메일"
            type="email"
            inputMode="email"
            autoComplete="email"
            autoCapitalize="none"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="tspoon@example.com"
            autoFocus
          />
          <TextField
            onBg
            label="비밀번호"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={wrongCredentials ? error.message : null}
          />
        </form>

        {unverified && (
          <div role="alert" className="mt-6 flex gap-3 rounded-[18px] bg-surface p-4">
            <Emoji name="mailbox" size={40} />
            <div className="min-w-0 flex-1">
              <p className="text-t5 font-semibold text-ink">이메일 인증을 마쳐야 로그인할 수 있어요</p>
              <p className="mt-0.5 text-t6 text-ink-3">가입할 때 받은 메일의 링크를 눌러주세요.</p>
              <Button
                size="sm"
                variant="weak"
                className="mt-3"
                loading={resend.isPending}
                onClick={() =>
                  resend.mutate(email.trim(), {
                    onSuccess: () => toast("인증 메일을 다시 보냈어요"),
                    onError: (e) => toast(isApiError(e) ? e.userMessage : "보내지 못했어요", "error"),
                  })
                }
              >
                인증 메일 다시 받기
              </Button>
            </div>
          </div>
        )}

        {locked && (
          <div role="alert" className="mt-6 flex gap-3 rounded-[18px] bg-surface p-4">
            <Emoji name="lock" size={40} />
            <div>
              <p className="text-t5 font-semibold text-ink">{error.message}</p>
              {error.retryAfter && (
                <p className="mt-0.5 text-t6 text-ink-3">{waitText(error.retryAfter)} 뒤에 다시 시도해 주세요.</p>
              )}
            </div>
          </div>
        )}

        {error && !unverified && !locked && !wrongCredentials && (
          <p role="alert" className="mt-4 text-t6 text-danger-ink">
            {error.userMessage}
          </p>
        )}

        <div className="mt-8 flex items-center justify-center gap-1 text-t6 text-ink-3">
          <Link href="/forgot-password" className="press rounded-lg px-2 py-1.5">
            비밀번호를 잊었어요
          </Link>
          <span aria-hidden className="h-3 w-px bg-line" />
          <Link href={`/signup?next=${encodeURIComponent(next)}`} className="press rounded-lg px-2 py-1.5 font-semibold text-primary-ink">
            처음이라면 가입하기
          </Link>
        </div>
      </div>

      <BottomCTA>
        <Button block type="submit" form="login-form" disabled={!valid} loading={login.isPending}>
          로그인
        </Button>
      </BottomCTA>
    </>
  );
}
