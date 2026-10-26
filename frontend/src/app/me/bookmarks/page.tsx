"use client";

import { ContentRow } from "@/components/domain/ContentArt";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { ButtonLink } from "@/components/ui/Button";
import { EmptyState, ErrorState, LoadMore, SkeletonRows } from "@/components/ui/States";
import { useMyBookmarks } from "@/lib/queries/contents";
import { flatten, totalOf } from "@/lib/queries/paging";

export default function BookmarksPage() {
  const bookmarks = useMyBookmarks();
  const items = flatten(bookmarks.data);
  const total = totalOf(bookmarks.data);

  return (
    <>
      <NavBar left={<BackButton fallback="/me" />} title="담아둔 배움" />
      <div className="px-5 pt-1 pb-4">
        <h1 className="text-t1 text-ink">담아둔 배움</h1>
        {total !== undefined && total > 0 && <p className="tabular mt-1 text-t5 text-ink-3">{total}개를 담아뒀어요</p>}
      </div>
      <section className="mx-3 rounded-section bg-surface py-2">
        {bookmarks.isPending ? (
          <SkeletonRows count={4} />
        ) : bookmarks.isError ? (
          <ErrorState error={bookmarks.error} onRetry={() => bookmarks.refetch()} />
        ) : items.length === 0 ? (
          <EmptyState
            emoji="bookmark"
            title="아직 담아둔 배움이 없어요"
            description="마음에 드는 콘텐츠를 담아두면 여기서 다시 볼 수 있어요"
            action={
              <ButtonLink href="/contents" size="md" variant="weak">
                배움 둘러보기
              </ButtonLink>
            }
          />
        ) : (
          items.map((c) => <ContentRow key={c.id} content={c} />)
        )}
        <LoadMore {...bookmarks} />
      </section>
    </>
  );
}
