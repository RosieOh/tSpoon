"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { NewPasswordFields, validateNewPassword } from "@/components/domain/NewPasswordFields";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { BottomCTA } from "@/components/shell/Screen";
import { Button, ButtonLink } from "@/components/ui/Button";
import { CheckMark, ResultView } from "@/components/ui/Result";
import { isApiError } from "@/lib/api";
import { useResetPassword } from "@/lib/queries/session";

export function ResetPasswordScreen() {
  const router = useRouter();
  const token = useSearchParams().get("token");
  const reset = useResetPassword();
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const check = validateNewPassword(password, confirm);

  // 필드 검증 실패가 아닌 400 은 링크(토큰) 문제다
  const linkUnusable = isApiError(reset.error, 400) && Object.keys(reset.error.errors).length === 0;
  if (!token || linkUnusable) {
    return (
      <>
        <NavBar left={<BackButton close fallback="/login" />} />
        <ResultView
          emoji="hourglass"
          title="링크를 쓸 수 없어요"
          description={reset.error && isApiError(reset.error) ? reset.error.userMessage : "링크가 올바르지 않아요."}
        >
          <ButtonLink href="/forgot-password" variant="weak" size="md" className="mt-8">
            재설정 메일 다시 받기
          </ButtonLink>
        </ResultView>
      </>
    );
  }

  if (reset.isSuccess) {
    return (
      <>
        <NavBar left={<BackButton close fallback="/login" />} />
        <ResultView icon={<CheckMark />} title="비밀번호를 바꿨어요" description="새 비밀번호로 로그인해 주세요. 다른 기기의 로그인은 끊겼어요." />
        <BottomCTA>
          <Button block onClick={() => router.replace("/login")}>
            로그인하기
          </Button>
        </BottomCTA>
      </>
    );
  }

  return (
    <>
      <NavBar left={<BackButton close fallback="/login" />} />
      <div className="px-5 pt-2">
        <h1 className="text-t1 text-ink">새 비밀번호를 만들어주세요</h1>
        <form
          id="reset-form"
          className="mt-9 space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (check.valid) reset.mutate({ token, newPassword: password });
          }}
        >
          <NewPasswordFields
            onBg
            password={password}
            confirm={confirm}
            onPassword={setPassword}
            onConfirm={setConfirm}
            serverError={reset.error && isApiError(reset.error) ? reset.error.userMessage : null}
          />
        </form>
      </div>
      <BottomCTA>
        <Button block type="submit" form="reset-form" disabled={!check.valid} loading={reset.isPending}>
          비밀번호 바꾸기
        </Button>
      </BottomCTA>
    </>
  );
}
