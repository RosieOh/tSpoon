"use client";

import { AnimatePresence, motion } from "motion/react";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useOverlay } from "@/components/overlays/Overlays";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { BottomCTA } from "@/components/shell/Screen";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/Field";
import { ResultView } from "@/components/ui/Result";
import { isApiError } from "@/lib/api";
import { useResendVerification, useSignup } from "@/lib/queries/session";
import { safeNext } from "@/lib/safe-next";

type Key = "email" | "password" | "name" | "phone";

type Step = {
  key: Key;
  title: string;
  description?: string;
  label: string;
  input: Pick<React.ComponentProps<"input">, "type" | "inputMode" | "autoComplete" | "placeholder">;
  validate: (value: string) => string | null;
};

const STEPS: Step[] = [
  {
    key: "email",
    title: "이메일을 입력해주세요",
    description: "로그인할 때 쓰고, 인증 메일을 보내드려요",
    label: "이메일",
    input: { type: "email", inputMode: "email", autoComplete: "email", placeholder: "tspoon@example.com" },
    validate: (v) => (/^\S+@\S+\.\S+$/.test(v.trim()) ? null : "이메일 형식을 확인해주세요"),
  },
  {
    key: "password",
    title: "비밀번호를 만들어주세요",
    description: "8자 이상이면 돼요",
    label: "비밀번호",
    input: { type: "password", autoComplete: "new-password" },
    validate: (v) => (v.length < 8 ? "8자 이상 입력해주세요" : v.length > 72 ? "72자까지 쓸 수 있어요" : null),
  },
  {
    key: "name",
    title: "이름을 알려주세요",
    description: "글과 답변에 이 이름이 보여요",
    label: "이름",
    input: { type: "text", autoComplete: "name", placeholder: "김티스푼" },
    validate: (v) => (v.trim() ? null : "이름을 입력해주세요"),
  },
  {
    key: "phone",
    title: "휴대폰 번호를 알려주세요",
    description: "행사 안내에 써요. 넘어가도 괜찮아요",
    label: "휴대폰 번호 (선택)",
    input: { type: "tel", inputMode: "tel", autoComplete: "tel", placeholder: "010-0000-0000" },
    validate: () => null,
  },
];

export function SignupScreen() {
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const { toast } = useOverlay();
  const signup = useSignup();
  const [step, setStep] = useState(0);
  const [values, setValues] = useState<Record<Key, string>>({ email: "", password: "", name: "", phone: "" });
  const [errors, setErrors] = useState<Partial<Record<Key, string>>>({});
  const [sentTo, setSentTo] = useState<string | null>(null);
  const inputRefs = useRef<Partial<Record<Key, HTMLInputElement | null>>>({});

  const current = STEPS[step];
  const isLast = step === STEPS.length - 1;
  const currentError = current.validate(values[current.key]);

  // 새 칸이 나타나면 그 칸에 바로 입력할 수 있게
  useEffect(() => {
    inputRefs.current[STEPS[step].key]?.focus();
  }, [step]);

  const submit = () => {
    signup.mutate(
      {
        email: values.email.trim(),
        password: values.password,
        name: values.name.trim(),
        phone: values.phone.trim() || undefined,
      },
      {
        onSuccess: (user) => setSentTo(user.email),
        onError: (e) => {
          if (!isApiError(e)) return toast("가입하지 못했어요", "error");
          if (e.status === 409) {
            setErrors({ email: "이미 가입한 이메일이에요. 로그인해 주세요" });
            setStep(0);
            return;
          }
          const fieldErrors = e.errors as Partial<Record<Key, string>>;
          const first = STEPS.findIndex((s) => fieldErrors[s.key]);
          if (first >= 0) {
            setErrors(fieldErrors);
            setStep(first);
          } else {
            toast(e.userMessage, "error");
          }
        },
      },
    );
  };

  const advance = () => {
    if (currentError) {
      setErrors((prev) => ({ ...prev, [current.key]: currentError }));
      return;
    }
    if (!isLast) {
      setStep(step + 1);
      return;
    }
    // 앞 칸을 고쳤을 수 있으니 보내기 전에 전부 다시 확인한다
    const invalid = STEPS.map((s) => [s.key, s.validate(values[s.key])] as const).filter(([, e]) => e !== null);
    if (invalid.length > 0) {
      setErrors(Object.fromEntries(invalid));
      inputRefs.current[invalid[0][0]]?.focus();
      return;
    }
    submit();
  };

  if (sentTo) {
    return <CheckEmail email={sentTo} onLogin={() => router.replace(`/login?next=${encodeURIComponent(next)}`)} />;
  }

  return (
    <>
      <NavBar left={<BackButton close fallback="/" />} />
      <div className="px-5 pt-2">
        <AnimatePresence mode="wait" initial={false}>
          <motion.div
            key={current.key}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.18 }}
          >
            <h1 className="text-t1 text-ink">{current.title}</h1>
            {current.description && <p className="mt-1.5 text-t5 text-ink-3">{current.description}</p>}
          </motion.div>
        </AnimatePresence>

        <form
          id="signup-form"
          className="mt-9 flex flex-col gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            advance();
          }}
        >
          <AnimatePresence initial={false}>
            {STEPS.slice(0, step + 1)
              .reverse()
              .map((s) => (
                <motion.div
                  key={s.key}
                  layout
                  initial={{ opacity: 0, y: -14 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ type: "spring", stiffness: 420, damping: 34 }}
                >
                  <TextField
                    onBg
                    ref={(el) => {
                      inputRefs.current[s.key] = el;
                    }}
                    label={s.label}
                    {...s.input}
                    autoCapitalize="none"
                    value={values[s.key]}
                    onChange={(e) => {
                      setValues((prev) => ({ ...prev, [s.key]: e.target.value }));
                      setErrors((prev) => ({ ...prev, [s.key]: undefined }));
                    }}
                    error={errors[s.key]}
                  />
                </motion.div>
              ))}
          </AnimatePresence>
          {/* Enter 로 다음 칸을 열 수 있게 */}
          <button type="submit" className="hidden" aria-hidden tabIndex={-1} />
        </form>
      </div>

      <BottomCTA>
        <Button block type="submit" form="signup-form" disabled={currentError !== null} loading={signup.isPending}>
          {isLast ? (values.phone.trim() ? "가입하기" : "건너뛰고 가입하기") : "다음"}
        </Button>
      </BottomCTA>
    </>
  );
}

function CheckEmail({ email, onLogin }: { email: string; onLogin: () => void }) {
  const resend = useResendVerification();
  const { toast } = useOverlay();
  const [cooldown, setCooldown] = useState(60);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  return (
    <>
      <NavBar left={<BackButton close fallback="/" />} />
      <ResultView
        emoji="mailbox"
        title="메일함을 확인해주세요"
        description={
          <>
            <b className="font-semibold text-ink-2">{email}</b>로 보낸
            <br />
            링크를 누르면 가입이 끝나요
          </>
        }
      >
        <Button
          variant="neutral"
          size="md"
          className="mt-8"
          disabled={cooldown > 0}
          loading={resend.isPending}
          onClick={() =>
            resend.mutate(email, {
              onSuccess: () => {
                toast("인증 메일을 다시 보냈어요");
                setCooldown(60);
              },
              onError: (e) => toast(isApiError(e) ? e.userMessage : "보내지 못했어요", "error"),
            })
          }
        >
          <span className="tabular">{cooldown > 0 ? `${cooldown}초 뒤에 다시 받을 수 있어요` : "인증 메일 다시 받기"}</span>
        </Button>
      </ResultView>
      <BottomCTA>
        <Button block onClick={onLogin}>
          인증을 마쳤어요
        </Button>
      </BottomCTA>
    </>
  );
}
