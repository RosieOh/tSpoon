import { Clock } from "lucide-react";
import Link from "next/link";
import { IconTile } from "@/components/ui/Emoji";
import { contentCategory, contentType } from "@/lib/labels";
import type { ContentSummary } from "@/lib/types";
import { CoverArt } from "./CoverArt";

/** 콘텐츠 표지 — 분야 색과 아이콘으로 그린 교과서 표지 (썸네일이 있으면 썸네일) */
export function ContentArt({
  content,
  variant,
  className,
  eager,
  priority = eager,
}: {
  content: Pick<ContentSummary, "category" | "thumbnailUrl">;
  variant: "tile" | "hero" | "banner";
  className?: string;
  eager?: boolean;
  /** 받기 우선순위를 높일지 (기본: eager 와 같음) */
  priority?: boolean;
}) {
  const visual = contentCategory[content.category];
  return (
    <CoverArt
      tint={visual.tint}
      emoji={visual.emoji}
      label={visual.label}
      imageUrl={content.thumbnailUrl}
      variant={variant}
      eager={eager}
      priority={priority}
      className={className}
    />
  );
}

/** 배움 화면의 2열 타일 */
export function ContentTile({
  content,
  eager,
  priority,
}: {
  content: ContentSummary;
  eager?: boolean;
  priority?: boolean;
}) {
  const type = contentType[content.type];
  return (
    <Link href={`/contents/${content.id}`} className="press group block">
      <div className="relative aspect-[4/3] overflow-hidden rounded-tile">
        <ContentArt content={content} variant="tile" eager={eager} priority={priority} />
        <span className="glass glass-on-cover absolute top-2.5 left-2.5 rounded-[10px] px-2 py-1 text-t8 font-semibold [--glass-blur:10px]">
          <span className="relative z-[2]">{type.label}</span>
        </span>
      </div>
      <p className="mt-2.5 line-clamp-2 text-t5 font-semibold text-ink">{content.title}</p>
      <p className="mt-1 flex items-center gap-2 text-t7 text-ink-3">
        <span className="truncate">{content.authorName}</span>
        {content.estimatedTime && (
          <span className="tabular flex shrink-0 items-center gap-0.5 text-ink-4">
            <Clock className="size-3" aria-hidden />
            {content.estimatedTime}분
          </span>
        )}
      </p>
    </Link>
  );
}

/** 목록 한 줄 — 북마크, 홈 */
export function ContentRow({ content }: { content: ContentSummary }) {
  const visual = contentCategory[content.category];
  const type = contentType[content.type];
  return (
    <Link href={`/contents/${content.id}`} className="press-row flex items-center gap-3.5 px-5 py-3.5">
      <IconTile emoji={visual.emoji} tint={visual.tint} size={48} />
      <span className="min-w-0 flex-1">
        <span className="block truncate text-t5 font-semibold text-ink">{content.title}</span>
        <span className="mt-0.5 flex items-center gap-2 text-t7 text-ink-3">
          <span>{type.label}</span>
          {content.estimatedTime && <span className="tabular">{content.estimatedTime}분</span>}
          {content.targetAge && <span>{content.targetAge}</span>}
        </span>
      </span>
    </Link>
  );
}
