"use client";

import { BoardRow } from "@/components/domain/BoardRow";
import { TabHeader, useQueryParam } from "@/components/shell/Screen";
import { Chips } from "@/components/ui/Chips";
import { EmptyState, ErrorState, LoadMore, SkeletonRows } from "@/components/ui/States";
import { boardCategory, keysOf } from "@/lib/labels";
import { useBoards } from "@/lib/queries/boards";
import { flatten } from "@/lib/queries/paging";
import type { BoardCategory } from "@/lib/types";

const CATEGORIES = keysOf(boardCategory);
const OPTIONS = [
  { value: null, label: "전체" },
  ...CATEGORIES.map((c) => ({ value: c, label: boardCategory[c].label })),
];

export function BoardsScreen() {
  const [category, setCategory] = useQueryParam<BoardCategory>("category", CATEGORIES);
  const boards = useBoards(category);
  const items = flatten(boards.data);

  return (
    <>
      <TabHeader title="커뮤니티" />
      <Chips label="게시판 종류" options={OPTIONS} value={category} onChange={setCategory} />
      <section aria-label="게시글" className="mx-3 mt-3 overflow-hidden rounded-section bg-surface py-1">
        {boards.isPending ? (
          <SkeletonRows count={6} icon={false} />
        ) : boards.isError ? (
          <ErrorState error={boards.error} onRetry={() => boards.refetch()} />
        ) : items.length === 0 ? (
          <EmptyState
            emoji="speech"
            title="아직 올라온 글이 없어요"
            description="첫 이야기를 들려주세요"
          />
        ) : (
          <ul className="divide-y divide-line">
            {items.map((board, index) => (
              <li key={board.id} className={index >= 5 ? "cv-row" : undefined}>
                <BoardRow board={board} showCategory={category === null} />
              </li>
            ))}
          </ul>
        )}
        <LoadMore {...boards} />
      </section>
    </>
  );
}
