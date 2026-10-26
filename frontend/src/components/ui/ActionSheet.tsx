"use client";

import clsx from "clsx";
import { Ellipsis, type LucideIcon } from "lucide-react";
import { useState } from "react";
import { GlassIconButton } from "@/components/shell/NavBar";
import { BottomSheet } from "./BottomSheet";

export type SheetAction = { label: string; icon: LucideIcon; onSelect: () => void; danger?: boolean };

/** 내비바의 "더보기" 버튼과 그 메뉴 */
export function MoreMenu({ actions, label = "더보기" }: { actions: SheetAction[]; label?: string }) {
  const [open, setOpen] = useState(false);
  if (actions.length === 0) return null;
  return (
    <>
      <GlassIconButton label={label} onClick={() => setOpen(true)}>
        <Ellipsis className="size-[22px]" aria-hidden />
      </GlassIconButton>
      <BottomSheet open={open} onClose={() => setOpen(false)}>
        <ul className="-mx-2 -mt-2">
          {actions.map((action) => (
            <li key={action.label}>
              <button
                type="button"
                onClick={() => {
                  setOpen(false);
                  action.onSelect();
                }}
                className={clsx(
                  "press-row flex w-full items-center gap-3.5 rounded-2xl px-3 py-3.5 text-left text-t4 font-semibold",
                  action.danger ? "text-danger-ink" : "text-ink",
                )}
              >
                <action.icon className="size-[22px]" aria-hidden />
                {action.label}
              </button>
            </li>
          ))}
        </ul>
      </BottomSheet>
    </>
  );
}
