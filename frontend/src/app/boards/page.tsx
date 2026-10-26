import { HydrationBoundary } from "@tanstack/react-query";
import type { Metadata } from "next";
import { Suspense } from "react";
import { ListScreenSkeleton } from "@/components/shell/Screen";
import { boardCategory, keysOf } from "@/lib/labels";
import { boardsQuery } from "@/lib/queries/keys";
import { pickParam } from "@/lib/server/params";
import { prefetch, seedFirstPage } from "@/lib/server/prefetch";
import { BoardsScreen } from "./boards-screen";

export const metadata: Metadata = { title: "커뮤니티", description: "학부모와 선생님이 나누는 육아·교육 이야기" };

export default function BoardsPage({ searchParams }: PageProps<"/boards">) {
  return (
    <Suspense fallback={<ListScreenSkeleton title="커뮤니티" />}>
      <BoardsWithData searchParams={searchParams} />
    </Suspense>
  );
}

async function BoardsWithData({ searchParams }: Pick<PageProps<"/boards">, "searchParams">) {
  const category = pickParam((await searchParams).category, keysOf(boardCategory));
  const state = await prefetch([seedFirstPage(boardsQuery(category))]);
  return (
    <HydrationBoundary state={state}>
      <BoardsScreen />
    </HydrationBoundary>
  );
}
