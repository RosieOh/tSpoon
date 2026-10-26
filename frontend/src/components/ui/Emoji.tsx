import clsx from "clsx";
import Image from "next/image";
import type { CSSProperties } from "react";

// 3D 아이콘 — Microsoft Fluent Emoji (MIT). public/emoji/*.png
export function Emoji({
  name,
  size = 24,
  alt = "",
  className,
  eager,
  priority = eager,
}: {
  name: string;
  size?: number;
  alt?: string;
  className?: string;
  /** 첫 화면에 보이는 그림 — 지연 로딩하지 않는다 */
  eager?: boolean;
  /** 첫 화면의 가장 큰 그림(LCP) — 다른 요청보다 먼저 받는다 (기본: eager 와 같음) */
  priority?: boolean;
}) {
  return (
    <Image
      src={`/emoji/${name}.png`}
      width={size}
      height={size}
      alt={alt}
      draggable={false}
      loading={eager ? "eager" : "lazy"}
      fetchPriority={priority ? "high" : undefined}
      className={clsx("pointer-events-none select-none", className)}
    />
  );
}

/** 카테고리 색 바탕 위의 3D 아이콘 — 토스 리스트의 왼쪽 아이콘 자리 */
export function IconTile({
  emoji,
  tint,
  size = 44,
  radius,
  className,
}: {
  emoji: string;
  tint: string;
  size?: number;
  radius?: number;
  className?: string;
}) {
  return (
    <span
      aria-hidden
      className={clsx("tint-bg grid shrink-0 place-items-center", className)}
      style={{ "--tint": tint, width: size, height: size, borderRadius: radius ?? Math.round(size * 0.32) } as CSSProperties}
    >
      <Emoji name={emoji} size={Math.round(size * 0.62)} />
    </span>
  );
}
