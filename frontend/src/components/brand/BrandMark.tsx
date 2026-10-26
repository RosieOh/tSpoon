import clsx from "clsx";

/**
 * 티스푼 마크 — CHUNJAE GREEN 사각 위의 흰 스푼, 스푼에 담긴 한 방울은 천재 노랑.
 * (천재교육 로고·해법 심볼은 상표라 그대로 쓰지 않는다)
 */
export function BrandMark({ size = 28, className }: { size?: number; className?: string }) {
  return (
    <svg
      viewBox="0 0 64 64"
      width={size}
      height={size}
      className={clsx("shrink-0", className)}
      aria-hidden
      focusable="false"
    >
      <rect width="64" height="64" rx="18" fill="#009944" />
      <g transform="rotate(-38 32 32)">
        <ellipse cx="32" cy="21" rx="10.5" ry="13" fill="#fff" />
        <rect x="29.25" y="31" width="5.5" height="25" rx="2.75" fill="#fff" />
        <circle cx="32" cy="22.5" r="5" fill="#ffc80a" />
      </g>
    </svg>
  );
}

export function BrandLogo({ size = 28, className }: { size?: number; className?: string }) {
  return (
    <span className={clsx("flex items-center gap-2", className)}>
      <BrandMark size={size} />
      <span className="text-t3 font-black tracking-[-0.04em] text-ink">티스푼</span>
    </span>
  );
}
