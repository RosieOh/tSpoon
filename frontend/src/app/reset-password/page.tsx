import type { Metadata } from "next";
import { Suspense } from "react";
import { ResetPasswordScreen } from "./reset-password-screen";

export const metadata: Metadata = { title: "비밀번호 다시 만들기" };

export default function ResetPasswordPage() {
  return (
    <Suspense>
      <ResetPasswordScreen />
    </Suspense>
  );
}
