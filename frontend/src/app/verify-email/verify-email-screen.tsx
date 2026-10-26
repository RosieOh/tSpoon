"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { useOverlay } from "@/components/overlays/Overlays";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { BottomCTA } from "@/components/shell/Screen";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/Field";
import { CheckMark, ResultView } from "@/components/ui/Result";
import { Spinner } from "@/components/ui/Spinner";
import { api, isApiError } from "@/lib/api";
import { useResendVerification } from "@/lib/queries/session";

// 링크 토큰은 한 번만 쓸 수 있다. 개발 모드의 effect 두 번 실행에도 한 번만 보내도록 결과를 나눠 쓴다
const attempts = new Map<string, Promise<void>>();

function confirmOnce(token: string) {
  let attempt = attempts.get(token);
  if (!attempt) {
    attempt = api<void>("/api/users/email-verification/confirm", { method: "POST", body: { token } });
    attempts.set(token, attempt);
  }
  return attempt;
}

type State = { kind: "pending" } | { kind: "done" } | { kind: "failed"; message: string };

export function VerifyEmailScreen() {
  const router = useRouter();
  const token = useSearchParams().get("token");
  const [state, setState] = useState<State>({ kind: "pending" });

  useEffect(() => {
    if (!token) return;
    let active = true;
    confirmOnce(token)
      .then(() => active && setState({ kind: "done" }))
      .catch((e) => active && setState({ kind: "failed", message: isApiError(e) ? e.userMessage : "인증하지 못했어요" }));
    return () => {
      active = false;
    };
  }, [token]);

  const current: State = token ? state : { kind: "failed", message: "인증 링크가 올바르지 않아요." };

  return (
    <>
      <NavBar left={<BackButton close fallback="/" />} />
      {current.kind === "pending" && (
        <ResultView icon={<Spinner className="size-10 text-primary-ink" />} title="이메일을 확인하고 있어요" />
      )}
      {current.kind === "done" && (
        <>
          <ResultView icon={<CheckMark />} title="이메일 인증을 마쳤어요" description="이제 티스푼에 로그인할 수 있어요" />
          <BottomCTA>
            <Button block onClick={() => router.replace("/login")}>
              로그인하기
            </Button>
          </BottomCTA>
        </>
      )}
      {current.kind === "failed" && <Failed message={current.message} />}
    </>
  );
}

function Failed({ message }: { message: string }) {
  const resend = useResendVerification();
  const { toast } = useOverlay();
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);

  return (
    <>
      <ResultView emoji="hourglass" title="링크를 쓸 수 없어요" description={message}>
        <form
          className="mt-8 w-full max-w-sm space-y-3 text-left"
          onSubmit={(e) => {
            e.preventDefault();
            resend.mutate(email.trim(), {
              onSuccess: () => {
                setSent(true);
                toast("인증 메일을 다시 보냈어요");
              },
              onError: (err) => toast(isApiError(err) ? err.userMessage : "보내지 못했어요", "error"),
            });
          }}
        >
          <TextField
            onBg
            label="가입한 이메일"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="tspoon@example.com"
            hint={sent ? "메일함을 확인해주세요. 새 링크는 24시간 동안 쓸 수 있어요" : undefined}
          />
          <Button block variant="weak" type="submit" disabled={!/\S+@\S+\.\S+/.test(email)} loading={resend.isPending}>
            인증 메일 다시 받기
          </Button>
        </form>
      </ResultView>
    </>
  );
}
