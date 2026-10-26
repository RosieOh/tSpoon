import { HydrationBoundary } from "@tanstack/react-query";
import type { Metadata } from "next";
import { Suspense } from "react";
import { ListScreenSkeleton } from "@/components/shell/Screen";
import { contentCategory, contentType, keysOf } from "@/lib/labels";
import { contentsQuery } from "@/lib/queries/keys";
import { pickParam } from "@/lib/server/params";
import { prefetch, seedFirstPage } from "@/lib/server/prefetch";
import { ContentsScreen } from "./contents-screen";

export const metadata: Metadata = { title: "배움", description: "선생님과 교육 전문가가 만든 학습 콘텐츠" };

export default function ContentsPage({ searchParams }: PageProps<"/contents">) {
  return (
    <Suspense fallback={<ListScreenSkeleton title="배움" />}>
      <ContentsWithData searchParams={searchParams} />
    </Suspense>
  );
}

async function ContentsWithData({ searchParams }: Pick<PageProps<"/contents">, "searchParams">) {
  const params = await searchParams;
  const category = pickParam(params.category, keysOf(contentCategory));
  const type = pickParam(params.type, keysOf(contentType));
  const state = await prefetch([seedFirstPage(contentsQuery({ category, type }))]);
  return (
    <HydrationBoundary state={state}>
      <ContentsScreen />
    </HydrationBoundary>
  );
}
