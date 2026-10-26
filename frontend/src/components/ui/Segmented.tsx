"use client";

import clsx from "clsx";
import { motion } from "motion/react";
import { useId } from "react";

/** iOS 세그먼트 컨트롤. 선택 표시가 스프링으로 미끄러진다 */
export function Segmented<T extends string>({
  options,
  value,
  onChange,
  label,
  className,
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
  label: string;
  className?: string;
}) {
  const id = useId();
  return (
    <div
      role="radiogroup"
      aria-label={label}
      className={clsx("flex rounded-[12px] bg-[color-mix(in_oklab,var(--ink)_7%,transparent)] p-[3px]", className)}
    >
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => onChange(option.value)}
            className={clsx(
              "relative h-9 flex-1 rounded-[9px] px-2 text-t6 font-semibold whitespace-nowrap transition-colors",
              selected ? "text-ink" : "text-ink-3",
            )}
          >
            {selected && (
              <motion.span
                layoutId={`seg-${id}`}
                className="absolute inset-0 rounded-[9px] bg-surface shadow-[0_1px_4px_rgb(0_0_0/0.08)] dark:bg-fill-strong"
                transition={{ type: "spring", stiffness: 520, damping: 38 }}
              />
            )}
            <span className="relative">{option.label}</span>
          </button>
        );
      })}
    </div>
  );
}
