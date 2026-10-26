"use client";

import clsx from "clsx";
import { useEffect, useRef, type ReactNode } from "react";

export type ChipOption<T extends string> = { value: T | null; label: string };

/** 가로로 넘기는 필터 칩. 선택된 칩은 진한 바탕 */
export function Chips<T extends string>({
  options,
  value,
  onChange,
  label,
  className,
  tone = "onBg",
  leading,
}: {
  options: ChipOption<T>[];
  value: T | null;
  onChange: (value: T | null) => void;
  label: string;
  className?: string;
  /** onBg: 회색 바탕 위(흰 칩), onSurface: 흰 바탕 위(회색 칩) */
  tone?: "onBg" | "onSurface";
  /** 칩 앞에 붙는 요소 (필터 버튼 등) */
  leading?: ReactNode;
}) {
  const ref = useRef<HTMLDivElement>(null);

  // 선택한 칩을 가로 스크롤 가운데로 — 세로 스크롤은 건드리지 않는다
  useEffect(() => {
    const container = ref.current;
    const selected = container?.querySelector<HTMLElement>("[aria-pressed='true']");
    if (!container || !selected) return;
    const left = selected.offsetLeft - container.clientWidth / 2 + selected.offsetWidth / 2;
    container.scrollTo({ left, behavior: "smooth" });
  }, [value]);

  return (
    <div
      ref={ref}
      role="group"
      aria-label={label}
      className={clsx("no-scrollbar relative flex gap-2 overflow-x-auto px-5 py-1", className)}
    >
      {leading}
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value ?? "all"}
            type="button"
            aria-pressed={selected}
            onClick={() => onChange(option.value)}
            className={clsx(
              "press h-9 shrink-0 rounded-full px-3.5 text-t6 font-semibold whitespace-nowrap",
              selected
                ? "bg-ink text-surface"
                : tone === "onBg"
                  ? "bg-surface text-ink-2"
                  : "bg-fill text-ink-2",
            )}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
