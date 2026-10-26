import clsx from "clsx";
import type { CSSProperties } from "react";
import { Emoji } from "@/components/ui/Emoji";
import { coverInk } from "@/lib/labels";

/*
 * 천재 교과서 표지 구성 — 원색 바탕, 흰 원 안의 캐릭터, 굵은 과목명.
 * 썸네일 이미지가 있으면 그것을 쓰고, 없으면 카테고리 색과 3D 아이콘으로 표지를 그린다.
 */

type Variant = "tile" | "hero" | "banner";

// emoji 는 불러올 이미지 해상도(px), 실제 크기는 원 지름에 비례(emojiWidth)
const layout: Record<Variant, { circle: string; label: string; emoji: number; emojiWidth: string }> = {
  // 2열 목록 타일 — 원이 오른쪽 아래로 잘려 나가며 표지 같은 여백을 만든다
  tile: {
    circle: "-right-[9%] -bottom-[16%] h-[80%] pr-[9%] pb-[14%]",
    label: "left-3 top-10 text-[19px]",
    emoji: 128,
    emojiWidth: "w-[62%]",
  },
  // 홈 "오늘의 한 스푼" — 아래쪽 유리 패널이 원 아랫부분을 비춰 굴절이 보인다
  hero: {
    circle: "right-[6%] top-[13%] h-[64%]",
    label: "left-5 top-[70px] text-[34px]",
    emoji: 160,
    emojiWidth: "w-[68%]",
  },
  // 상세 화면 맨 위
  banner: {
    circle: "right-[7%] top-[22%] h-[60%]",
    label: "left-5 bottom-12 text-[38px]",
    emoji: 160,
    emojiWidth: "w-[64%]",
  },
};

export function CoverArt({
  tint,
  emoji,
  label,
  imageUrl,
  variant,
  eager,
  priority = eager,
  className,
}: {
  tint: string;
  emoji: string;
  label?: string;
  imageUrl?: string | null;
  variant: Variant;
  eager?: boolean;
  priority?: boolean;
  className?: string;
}) {
  if (imageUrl) {
    return (
      // 외부 썸네일 주소는 어떤 호스트일지 알 수 없어 최적화 없이 그대로 쓴다
      // eslint-disable-next-line @next/next/no-img-element
      <img src={imageUrl} alt="" className={clsx("size-full object-cover", className)} />
    );
  }
  const spec = layout[variant];
  return (
    <div
      aria-hidden
      className={clsx("relative size-full overflow-hidden", className)}
      style={{ backgroundColor: tint, color: coverInk(tint) } as CSSProperties}
    >
      {/* 인쇄물 같은 빛 번짐 */}
      <div className="absolute inset-0 bg-[radial-gradient(110%_80%_at_15%_0%,rgb(255_255_255/0.26),transparent_60%)]" />
      <div
        className={clsx(
          "absolute grid aspect-square place-items-center rounded-full bg-white/95 shadow-[0_10px_30px_-8px_rgb(0_0_0/0.25)]",
          spec.circle,
        )}
      >
        <Emoji
          name={emoji}
          size={spec.emoji}
          eager={eager}
          priority={priority}
          className={clsx("h-auto drop-shadow-[0_8px_14px_rgb(0_0_0/0.16)]", spec.emojiWidth)}
        />
      </div>
      {label && (
        <span className={clsx("absolute leading-none font-black tracking-[-0.04em]", spec.label)}>{label}</span>
      )}
    </div>
  );
}
