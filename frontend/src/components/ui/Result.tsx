"use client";

import clsx from "clsx";
import { motion } from "motion/react";
import type { ReactNode } from "react";
import { Emoji } from "./Emoji";

/** 체크가 그려지는 완료 표시 */
export function CheckMark({ size = 76 }: { size?: number }) {
  return (
    <motion.svg
      viewBox="0 0 72 72"
      width={size}
      height={size}
      initial={{ scale: 0.6, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      transition={{ type: "spring", stiffness: 380, damping: 18 }}
      aria-hidden
    >
      <circle cx="36" cy="36" r="36" fill="var(--primary)" />
      <motion.path
        d="M22 37.5l9.5 9.5L51 27"
        fill="none"
        stroke="white"
        strokeWidth="6"
        strokeLinecap="round"
        strokeLinejoin="round"
        initial={{ pathLength: 0 }}
        animate={{ pathLength: 1 }}
        transition={{ delay: 0.18, duration: 0.38, ease: "easeOut" }}
      />
    </motion.svg>
  );
}

/** 결과 한 화면 — 가운데 아이콘, 한 줄 제목, 설명 */
export function ResultView({
  icon,
  emoji,
  title,
  description,
  children,
  className,
}: {
  icon?: ReactNode;
  emoji?: string;
  title: ReactNode;
  description?: ReactNode;
  children?: ReactNode;
  className?: string;
}) {
  return (
    <div className={clsx("flex flex-col items-center px-6 pt-[14vh] text-center", className)}>
      {icon ?? (emoji && <Emoji name={emoji} size={88} eager />)}
      <h1 className="mt-6 text-t1 text-ink">{title}</h1>
      {description && <div className="mt-2 text-t5 text-ink-3">{description}</div>}
      {children}
    </div>
  );
}
