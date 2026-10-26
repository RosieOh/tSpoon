"use client";

import { AnimatePresence, motion, useDragControls } from "motion/react";
import { useEffect, useId, useLayoutEffect, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { LiquidGlass } from "@/components/glass/LiquidGlass";
import { useIsClient } from "@/lib/hooks";

/**
 * 떠 있는 유리 시트. 손잡이를 끌어내리거나 바깥을 누르거나 Esc 로 닫는다.
 * 화면이 Activity 로 숨겨질 때(다른 화면으로 이동)도 닫는다 — 포털은 숨겨지는 트리 밖에 있기 때문.
 */
export function BottomSheet({
  open,
  onClose,
  title,
  description,
  children,
}: {
  open: boolean;
  onClose: () => void;
  title?: ReactNode;
  description?: ReactNode;
  children: ReactNode;
}) {
  const isClient = useIsClient();
  const titleId = useId();
  const drag = useDragControls();
  const panelRef = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  useLayoutEffect(() => {
    onCloseRef.current = onClose;
  });

  useLayoutEffect(() => () => onCloseRef.current(), []);

  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement as HTMLElement | null;
    const { overflow } = document.body.style;
    document.body.style.overflow = "hidden";
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onCloseRef.current();
    };
    window.addEventListener("keydown", onKey);
    requestAnimationFrame(() => {
      const target =
        panelRef.current?.querySelector<HTMLElement>("[data-autofocus]") ??
        panelRef.current?.querySelector<HTMLElement>("input, textarea, button:not([data-sheet-handle])");
      (target ?? panelRef.current)?.focus({ preventScroll: true });
    });
    return () => {
      document.body.style.overflow = overflow;
      window.removeEventListener("keydown", onKey);
      previous?.focus?.({ preventScroll: true });
    };
  }, [open]);

  if (!isClient) return null;

  return createPortal(
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-50">
          <motion.div
            className="absolute inset-0 bg-[var(--dim)]"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={onClose}
          />
          <motion.div
            ref={panelRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={title ? titleId : undefined}
            tabIndex={-1}
            className="column-fixed bottom-0 px-2 pb-[max(8px,env(safe-area-inset-bottom))] outline-none"
            initial={{ y: "110%" }}
            animate={{ y: 0 }}
            exit={{ y: "110%" }}
            transition={{ type: "spring", stiffness: 420, damping: 38 }}
            drag="y"
            dragControls={drag}
            dragListener={false}
            dragConstraints={{ top: 0, bottom: 0 }}
            dragElastic={{ top: 0.05, bottom: 0.7 }}
            onDragEnd={(_, info) => {
              if (info.offset.y > 90 || info.velocity.y > 600) onClose();
            }}
          >
            <LiquidGlass radius={34} thick bezel={22} strength={40} blur={24} className="max-h-[86dvh] overflow-hidden">
              <div
                data-sheet-handle
                className="flex cursor-grab touch-none justify-center pt-2.5 pb-1 active:cursor-grabbing"
                onPointerDown={(e) => drag.start(e)}
              >
                <span className="h-[5px] w-9 rounded-full bg-ink-5/60" />
              </div>
              <div className="no-scrollbar max-h-[calc(86dvh-24px)] overflow-y-auto px-6 pt-3 pb-5">
                {title && (
                  <div className="mb-5" onPointerDown={(e) => drag.start(e)}>
                    <h2 id={titleId} className="text-t2 text-ink">
                      {title}
                    </h2>
                    {description && <p className="mt-1.5 text-t5 text-ink-3">{description}</p>}
                  </div>
                )}
                {children}
              </div>
            </LiquidGlass>
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body,
  );
}
