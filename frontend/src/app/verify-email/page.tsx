import type { Metadata } from "next";
import { Suspense } from "react";
import { VerifyEmailScreen } from "./verify-email-screen";

export const metadata: Metadata = { title: "이메일 인증" };

export default function VerifyEmailPage() {
  return (
    <Suspense>
      <VerifyEmailScreen />
    </Suspense>
  );
}
