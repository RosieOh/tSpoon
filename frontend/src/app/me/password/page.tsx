"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { NewPasswordFields, validateNewPassword } from "@/components/domain/NewPasswordFields";
import { useOverlay } from "@/components/overlays/Overlays";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { BottomCTA } from "@/components/shell/Screen";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/Field";
import { isApiError } from "@/lib/api";
import { useChangePassword } from "@/lib/queries/session";

export default function ChangePasswordPage() {
  const router = useRouter();
  const { toast } = useOverlay();
  const change = useChangePassword();
  const [current, setCurrent] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const check = validateNewPassword(password, confirm);
  const wrongCurrent = change.error && isApiError(change.error) && change.error.code === "INVALID_CURRENT_PASSWORD";

  return (
    <>
      <NavBar left={<BackButton fallback="/me" />} title="비밀번호 바꾸기" alwaysShowTitle />
      <form
        id="password-form"
        className="mx-3 mt-2 space-y-5 rounded-section bg-surface p-5"
        onSubmit={(e) => {
          e.preventDefault();
          if (!check.valid || !current) return;
          change.mutate(
            { currentPassword: current, newPassword: password },
            {
              onSuccess: () => {
                setCurrent("");
                setPassword("");
                setConfirm("");
                toast("비밀번호를 바꿨어요");
                router.back();
              },
              onError: (err) => {
                if (!(isApiError(err) && err.code === "INVALID_CURRENT_PASSWORD")) {
                  toast(isApiError(err) ? err.userMessage : "바꾸지 못했어요", "error");
                }
              },
            },
          );
        }}
      >
        <TextField
          label="지금 비밀번호"
          type="password"
          autoComplete="current-password"
          value={current}
          onChange={(e) => {
            setCurrent(e.target.value);
            change.reset();
          }}
          error={wrongCurrent ? "지금 비밀번호가 맞지 않아요" : null}
        />
        <div className="h-px bg-line" />
        <NewPasswordFields password={password} confirm={confirm} onPassword={setPassword} onConfirm={setConfirm} />
        <p className="text-t7 text-ink-4">바꾸면 다른 기기의 로그인은 모두 끊겨요. 이 기기는 그대로 써요.</p>
      </form>
      <BottomCTA>
        <Button block type="submit" form="password-form" disabled={!check.valid || !current} loading={change.isPending}>
          비밀번호 바꾸기
        </Button>
      </BottomCTA>
    </>
  );
}
