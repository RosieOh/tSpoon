import { HydrationBoundary } from "@tanstack/react-query";
import type { Metadata } from "next";
import { Suspense } from "react";
import { DetailScreenSkeleton } from "@/components/shell/Screen";
import { questionQuery } from "@/lib/queries/keys";
import { toId } from "@/lib/server/params";
import { getPublic, prefetch, seedOne } from "@/lib/server/prefetch";
import type { Question } from "@/lib/types";
import { QuestionDetailScreen } from "./question-detail-screen";

export async function generateMetadata({ params }: PageProps<"/questions/[id]">): Promise<Metadata> {
  const id = toId((await params).id);
  const question = id ? await getPublic<Question>(questionQuery(id).path) : null;
  if (!question) return { title: "질문" };
  return {
    title: question.title,
    description: question.content.slice(0, 120),
    openGraph: { title: `Q. ${question.title}` },
  };
}

export default function QuestionDetailPage({ params }: PageProps<"/questions/[id]">) {
  return (
    <Suspense fallback={<DetailScreenSkeleton fallback="/questions" />}>
      <QuestionWithData params={params} />
    </Suspense>
  );
}

async function QuestionWithData({ params }: Pick<PageProps<"/questions/[id]">, "params">) {
  const id = toId((await params).id);
  const state = await prefetch(id ? [seedOne(questionQuery(id))] : []);
  return (
    <HydrationBoundary state={state}>
      <QuestionDetailScreen />
    </HydrationBoundary>
  );
}
