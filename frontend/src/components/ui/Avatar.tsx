import clsx from "clsx";
import type { CSSProperties } from "react";
import { HUE } from "@/lib/labels";

const TINTS = [HUE.green, HUE.sky, HUE.orange, HUE.purple, HUE.pink, HUE.teal, HUE.blue, HUE.coral];

function tintOf(name: string) {
  let hash = 0;
  for (const ch of name) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  return TINTS[hash % TINTS.length];
}

// 한글 이름은 성을 뺀 마지막 글자가 더 친근하다
function initialOf(name: string) {
  const trimmed = name.trim();
  if (!trimmed) return "?";
  return /^[가-힣]{2,4}$/.test(trimmed) ? trimmed.slice(-1) : trimmed.slice(0, 1).toUpperCase();
}

export function Avatar({ name, size = 40, className }: { name: string | null; size?: number; className?: string }) {
  const label = name ?? "알 수 없음";
  const tint = tintOf(label);
  return (
    <span
      aria-hidden
      className={clsx("tint-bg-strong tint-ink grid shrink-0 place-items-center rounded-full font-bold", className)}
      style={{ "--tint": tint, width: size, height: size, fontSize: Math.round(size * 0.4) } as CSSProperties}
    >
      {name ? initialOf(label) : "?"}
    </span>
  );
}
