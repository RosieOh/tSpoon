"use client";

import { TextField } from "@/components/ui/Field";

/** 새 비밀번호 + 확인. 둘 다 맞으면 valid */
export function validateNewPassword(password: string, confirm: string) {
  const tooShort = password.length > 0 && password.length < 8;
  const tooLong = password.length > 72;
  const mismatch = confirm.length > 0 && confirm !== password;
  return {
    valid: password.length >= 8 && !tooLong && confirm === password,
    passwordError: tooShort ? "8자 이상 입력해주세요" : tooLong ? "72자까지 쓸 수 있어요" : null,
    confirmError: mismatch ? "비밀번호가 서로 달라요" : null,
  };
}

export function NewPasswordFields({
  password,
  confirm,
  onPassword,
  onConfirm,
  serverError,
  onBg,
}: {
  password: string;
  confirm: string;
  onPassword: (value: string) => void;
  onConfirm: (value: string) => void;
  serverError?: string | null;
  onBg?: boolean;
}) {
  const check = validateNewPassword(password, confirm);
  return (
    <>
      <TextField
        onBg={onBg}
        label="새 비밀번호"
        type="password"
        autoComplete="new-password"
        value={password}
        onChange={(e) => onPassword(e.target.value)}
        hint="8자 이상"
        error={check.passwordError ?? serverError}
      />
      <TextField
        onBg={onBg}
        label="새 비밀번호 확인"
        type="password"
        autoComplete="new-password"
        value={confirm}
        onChange={(e) => onConfirm(e.target.value)}
        error={check.confirmError}
      />
    </>
  );
}
