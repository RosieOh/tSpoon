import { HydrationBoundary } from "@tanstack/react-query";
import type { Metadata } from "next";
import { Suspense } from "react";
import { DetailScreenSkeleton } from "@/components/shell/Screen";
import { boardQuery, commentsQuery } from "@/lib/queries/keys";
import { toId } from "@/lib/server/params";
import { getPublic, prefetch, seedOne } from "@/lib/server/prefetch";
import type { Board } from "@/lib/types";
import { BoardDetailScreen } from "./board-detail-screen";

// 같은 요청 안에서는 페이지가 가져올 데이터를 그대로 쓴다 (조회수는 한 번만 오른다)
export async function generateMetadata({ params }: PageProps<"/boards/[id]">): Promise<Metadata> {
  const id = toId((await params).id);
  const board = id ? await getPublic<Board>(boardQuery(id).path) : null;
  if (!board) return { title: "커뮤니티" };
  return { title: board.title, description: board.content.slice(0, 120), openGraph: { title: board.title } };
}

export default function BoardDetailPage({ params }: PageProps<"/boards/[id]">) {
  return (
    <Suspense fallback={<DetailScreenSkeleton fallback="/boards" />}>
      <BoardWithData params={params} />
    </Suspense>
  );
}

async function BoardWithData({ params }: Pick<PageProps<"/boards/[id]">, "params">) {
  const id = toId((await params).id);
  const state = await prefetch(id ? [seedOne(boardQuery(id)), seedOne(commentsQuery(id))] : []);
  return (
    <HydrationBoundary state={state}>
      <BoardDetailScreen />
    </HydrationBoundary>
  );
}
