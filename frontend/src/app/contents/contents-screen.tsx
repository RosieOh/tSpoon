"use client";

import clsx from "clsx";
import { Check, ChevronDown } from "lucide-react";
import { useState } from "react";
import { ContentTile } from "@/components/domain/ContentArt";
import { TabHeader, useQueryParam } from "@/components/shell/Screen";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { Chips } from "@/components/ui/Chips";
import { Emoji } from "@/components/ui/Emoji";
import { EmptyState, ErrorState, LoadMore, SkeletonBlock } from "@/components/ui/States";
import { contentCategory, contentType, keysOf } from "@/lib/labels";
import { useContents } from "@/lib/queries/contents";
import { flatten, totalOf } from "@/lib/queries/paging";
import type { ContentCategory, ContentType } from "@/lib/types";

const CATEGORIES = keysOf(contentCategory);
const TYPES = keysOf(contentType);

export function ContentsScreen() {
  const [category, setCategory] = useQueryParam<ContentCategory>("category", CATEGORIES);
  const [type, setType] = useQueryParam<ContentType>("type", TYPES);
  const [typeSheet, setTypeSheet] = useState(false);
  const contents = useContents({ category, type });
  const items = flatten(contents.data);
  const total = totalOf(contents.data);

  return (
    <>
      <TabHeader title="배움">
        <p className="mt-1 text-t5 text-ink-3">선생님과 전문가가 만든 콘텐츠예요</p>
      </TabHeader>
      <Chips
        label="분야"
        options={[{ value: null, label: "전체" }, ...CATEGORIES.map((c) => ({ value: c, label: contentCategory[c].label }))]}
        value={category}
        onChange={setCategory}
        leading={
          <button
            type="button"
            onClick={() => setTypeSheet(true)}
            aria-haspopup="dialog"
            className={clsx(
              "press flex h-9 shrink-0 items-center gap-1 rounded-full border px-3.5 text-t6 font-semibold whitespace-nowrap",
              type ? "border-primary bg-primary-weak text-primary-weak-ink" : "border-line bg-surface text-ink-2",
            )}
          >
            {type ? contentType[type].label : "모든 유형"}
            <ChevronDown className="size-4" aria-hidden />
          </button>
        }
      />

      {total !== undefined && (
        <p className="tabular px-5 pt-4 text-t7 text-ink-3" aria-live="polite">
          {total}개의 콘텐츠
        </p>
      )}

      <div className="px-4 pt-3">
        {contents.isPending ? (
          <div className="grid grid-cols-2 gap-x-3 gap-y-6">
            {Array.from({ length: 6 }, (_, i) => (
              <div key={i}>
                <SkeletonBlock className="aspect-[4/3] rounded-tile" />
                <SkeletonBlock className="mt-2.5 h-4 w-4/5" />
                <SkeletonBlock className="mt-1.5 h-3.5 w-1/2" />
              </div>
            ))}
          </div>
        ) : contents.isError ? (
          <ErrorState error={contents.error} onRetry={() => contents.refetch()} />
        ) : items.length === 0 ? (
          <EmptyState emoji="books" title="조건에 맞는 콘텐츠가 없어요" description="다른 분야나 유형을 골라보세요" />
        ) : (
          <ul className="grid grid-cols-2 gap-x-3 gap-y-6">
            {items.map((content, index) => (
              <li key={content.id} className={index >= 4 ? "cv-tile" : undefined}>
                {/* 첫 두 줄(네 장)은 첫 화면에 보이므로 바로 받고, 첫 줄은 가장 먼저 */}
                <ContentTile content={content} eager={index < 4} priority={index < 2} />
              </li>
            ))}
          </ul>
        )}
        <LoadMore {...contents} />
      </div>

      <BottomSheet open={typeSheet} onClose={() => setTypeSheet(false)} title="어떤 유형을 볼까요?">
        <ul className="-mx-2" role="listbox" aria-label="콘텐츠 유형">
          {[null, ...TYPES].map((t) => {
            const selected = t === type;
            return (
              <li key={t ?? "all"} role="option" aria-selected={selected}>
                <button
                  type="button"
                  onClick={() => {
                    setType(t);
                    setTypeSheet(false);
                  }}
                  className="press-row flex w-full items-center gap-3.5 rounded-2xl px-3 py-2.5 text-left"
                >
                  <span className="grid size-10 place-items-center rounded-[12px] bg-fill">
                    <Emoji name={t ? contentType[t].emoji : "sparkles"} size={26} />
                  </span>
                  <span className="flex-1 text-t4 font-semibold text-ink">{t ? contentType[t].label : "모든 유형"}</span>
                  {selected && <Check className="size-5 text-primary-ink" strokeWidth={2.6} aria-hidden />}
                </button>
              </li>
            );
          })}
        </ul>
      </BottomSheet>
    </>
  );
}
