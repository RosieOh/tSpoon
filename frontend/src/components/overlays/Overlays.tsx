"use client";

import { CircleCheck, CircleAlert } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useRouter } from "next/navigation";
import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from "react";
import { LiquidGlass } from "@/components/glass/LiquidGlass";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { Button } from "@/components/ui/Button";
import { BrandMark } from "@/components/brand/BrandMark";
import { useSession } from "@/lib/queries/session";

type ToastTone = "success" | "error";
type ConfirmOptions = {
  title: string;
  description?: string;
  confirmLabel: string;
  cancelLabel?: string;
  danger?: boolean;
};

type OverlayApi = {
  toast: (message: string, tone?: ToastTone) => void;
  confirm: (options: ConfirmOptions) => Promise<boolean>;
  /** 로그인했으면 true. 아니면 로그인 안내 시트를 띄우고 false */
  requireAuth: (reason?: string) => boolean;
};

const OverlayContext = createContext<OverlayApi | null>(null);

export function useOverlay() {
  const ctx = useContext(OverlayContext);
  if (!ctx) throw new Error("useOverlay 는 OverlayProvider 안에서 써야 합니다");
  return ctx;
}

export function OverlayProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const { user } = useSession();
  const [toast, setToast] = useState<{ id: number; message: string; tone: ToastTone } | null>(null);
  const toastTimer = useRef<ReturnType<typeof setTimeout>>(undefined);
  const [confirmState, setConfirmState] = useState<(ConfirmOptions & { resolve: (ok: boolean) => void }) | null>(null);
  const [authReason, setAuthReason] = useState<string | null>(null);

  const showToast = useCallback((message: string, tone: ToastTone = "success") => {
    clearTimeout(toastTimer.current);
    setToast({ id: Date.now(), message, tone });
    toastTimer.current = setTimeout(() => setToast(null), 2600);
  }, []);

  const confirm = useCallback(
    (options: ConfirmOptions) => new Promise<boolean>((resolve) => setConfirmState({ ...options, resolve })),
    [],
  );

  const requireAuth = useCallback(
    (reason?: string) => {
      if (user) return true;
      setAuthReason(reason ?? "");
      return false;
    },
    [user],
  );

  const api = useMemo(() => ({ toast: showToast, confirm, requireAuth }), [showToast, confirm, requireAuth]);

  const closeConfirm = (ok: boolean) => {
    confirmState?.resolve(ok);
    setConfirmState(null);
  };

  const goAuth = (path: "/login" | "/signup") => {
    setAuthReason(null);
    const next = window.location.pathname + window.location.search;
    router.push(`${path}?next=${encodeURIComponent(next)}`);
  };

  return (
    <OverlayContext.Provider value={api}>
      {children}

      <div aria-live="polite" className="column-fixed pointer-events-none bottom-[calc(env(safe-area-inset-bottom)+100px)] z-[60] flex justify-center px-4 lg:bottom-8">
        <AnimatePresence>
          {toast && (
            <motion.div
              key={toast.id}
              initial={{ opacity: 0, y: 16, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 8, scale: 0.98 }}
              transition={{ type: "spring", stiffness: 500, damping: 36 }}
            >
              <LiquidGlass radius={26} thick blur={20} className="flex items-center gap-2 py-3 pr-5 pl-4">
                {toast.tone === "success" ? (
                  <CircleCheck className="size-5 shrink-0 text-positive" aria-hidden />
                ) : (
                  <CircleAlert className="size-5 shrink-0 text-danger-ink" aria-hidden />
                )}
                <span className="text-t5 font-semibold text-ink">{toast.message}</span>
              </LiquidGlass>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      <BottomSheet
        open={confirmState !== null}
        onClose={() => closeConfirm(false)}
        title={confirmState?.title}
        description={confirmState?.description}
      >
        <div className="flex gap-2">
          <Button variant="neutral" block onClick={() => closeConfirm(false)}>
            {confirmState?.cancelLabel ?? "닫기"}
          </Button>
          <Button variant={confirmState?.danger ? "danger" : "primary"} block onClick={() => closeConfirm(true)} data-autofocus>
            {confirmState?.confirmLabel}
          </Button>
        </div>
      </BottomSheet>

      <BottomSheet open={authReason !== null} onClose={() => setAuthReason(null)}>
        <div className="flex flex-col items-center pt-2 text-center">
          <BrandMark size={72} />
          <h2 className="mt-4 text-t2 text-ink">로그인이 필요해요</h2>
          <p className="mt-1.5 text-t5 text-ink-3">
            {authReason ? `${authReason} 로그인해 주세요.` : "로그인하면 티스푼의 모든 기능을 쓸 수 있어요."}
          </p>
        </div>
        <div className="mt-7 space-y-2">
          <Button block onClick={() => goAuth("/login")} data-autofocus>
            로그인하기
          </Button>
          <Button variant="ghost" block size="md" onClick={() => goAuth("/signup")}>
            처음이라면 가입하기
          </Button>
        </div>
      </BottomSheet>
    </OverlayContext.Provider>
  );
}
