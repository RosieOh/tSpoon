"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useOverlay } from "@/components/overlays/Overlays";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { BottomCTA } from "@/components/shell/Screen";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/Field";
import { ResultView } from "@/components/ui/Result";
import { isApiError } from "@/lib/api";
import { useRequestPasswordReset } from "@/lib/queries/session";
import { waitText } from "@/lib/safe-next";

export default function ForgotPasswordPage() {
  const router = useRouter();
  const { toast } = useOverlay();
  const request = useRequestPasswordReset();
  const [email, setEmail] = useState("");
  const [sentTo, setSentTo] = useState<string | null>(null);
  const valid = /^\S+@\S+\.\S+$/.test(email.trim());

  if (sentTo) {
    return (
      <>
        <NavBar left={<BackButton close fallback="/login" />} />
        <ResultView
          emoji="mailbox"
          title="메일을 보냈어요"
          description={
            <>
              <b className="font-semibold text-ink-2">{sentTo}</b>로 가입했다면
              <br />
              비밀번호를 다시 만드는 링크가 도착해요. 링크는 30분 동안 쓸 수 있어요.
            </>
          }
        />
        <BottomCTA>
          <Button block onClick={() => router.replace("/login")}>
            로그인으로 돌아가기
          </Button>
        </BottomCTA>
      </>
    );
  }

  return (
    <>
      <NavBar left={<BackButton fallback="/login" />} />
      <div className="px-5 pt-2">
        <h1 className="text-t1 text-ink">
          가입한 이메일을
          <br />
          알려주세요
        </h1>
        <p className="mt-1.5 text-t5 text-ink-3">비밀번호를 다시 만드는 링크를 보내드려요</p>
        <form
          id="forgot-form"
          className="mt-9"
          onSubmit={(e) => {
            e.preventDefault();
            if (!valid) return;
            request.mutate(email.trim(), {
              onSuccess: () => setSentTo(email.trim()),
              onError: (err) =>
                toast(
                  isApiError(err, 429) && err.retryAfter
                    ? `${waitText(err.retryAfter)} 뒤에 다시 시도해 주세요`
                    : isApiError(err)
                      ? err.userMessage
                      : "보내지 못했어요",
                  "error",
                ),
            });
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
        </form>
      </div>
      <BottomCTA>
        <Button block type="submit" form="forgot-form" disabled={!valid} loading={request.isPending}>
          재설정 메일 받기
        </Button>
      </BottomCTA>
    </>
  );
}
