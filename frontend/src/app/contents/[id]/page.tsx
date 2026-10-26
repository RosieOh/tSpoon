import { HydrationBoundary } from "@tanstack/react-query";
import type { Metadata } from "next";
import { Suspense } from "react";
import { DetailScreenSkeleton } from "@/components/shell/Screen";
import { contentQuery } from "@/lib/queries/keys";
import { toId } from "@/lib/server/params";
import { getPublic, prefetch, seedOne } from "@/lib/server/prefetch";
import type { Content } from "@/lib/types";
import { ContentDetailScreen } from "./content-detail-screen";

// 임시저장 글처럼 로그인해야 보이는 콘텐츠는 서버에서 못 가져오므로 브라우저가 이어서 가져온다
export async function generateMetadata({ params }: PageProps<"/contents/[id]">): Promise<Metadata> {
  const id = toId((await params).id);
  const content = id ? await getPublic<Content>(contentQuery(id).path) : null;
  if (!content) return { title: "배움" };
  return {
    title: content.title,
    description: content.description,
    openGraph: { title: content.title, description: content.description },
  };
}

export default function ContentDetailPage({ params }: PageProps<"/contents/[id]">) {
  return (
    <Suspense fallback={<DetailScreenSkeleton fallback="/contents" />}>
      <ContentWithData params={params} />
    </Suspense>
  );
}

async function ContentWithData({ params }: Pick<PageProps<"/contents/[id]">, "params">) {
  const id = toId((await params).id);
  const state = await prefetch(id ? [seedOne(contentQuery(id))] : []);
  return (
    <HydrationBoundary state={state}>
      <ContentDetailScreen />
    </HydrationBoundary>
  );
}
