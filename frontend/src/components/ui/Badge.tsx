import clsx from "clsx";
import type { CSSProperties, ReactNode } from "react";

type Tone = "blue" | "brand" | "green" | "red" | "orange" | "grey";

const tones: Record<Tone, string> = {
  // 정보·안내 — CHUNJAE SKY BLUE
  blue: "bg-info-weak text-info-ink",
  // 브랜드 강조 — CHUNJAE GREEN
  brand: "bg-primary-weak text-primary-weak-ink",
  green: "bg-positive-weak text-primary-weak-ink",
  red: "bg-danger-weak text-danger-ink",
  orange: "bg-warn-weak text-warn-ink",
  grey: "bg-fill text-ink-3",
};

/** 작은 꼬리표. tone 대신 tint(카테고리 색)를 주면 그 색으로 */
export function Badge({
  tone = "grey",
  tint,
  children,
  className,
  icon,
}: {
  tone?: Tone;
  tint?: string;
  children: ReactNode;
  className?: string;
  icon?: ReactNode;
}) {
  return (
    <span
      className={clsx(
        "inline-flex h-6 shrink-0 items-center gap-1 rounded-[7px] px-2 text-t8 font-semibold whitespace-nowrap",
        tint ? "tint-bg-strong tint-ink" : tones[tone],
        className,
      )}
      style={tint ? ({ "--tint": tint } as CSSProperties) : undefined}
    >
      {icon}
      {children}
    </span>
  );
}
