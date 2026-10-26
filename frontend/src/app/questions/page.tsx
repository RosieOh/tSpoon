import { HydrationBoundary } from "@tanstack/react-query";
import type { Metadata } from "next";
import { Suspense } from "react";
import { ListScreenSkeleton } from "@/components/shell/Screen";
import { keysOf, questionCategory } from "@/lib/labels";
import { QUESTION_STATUS_FILTERS, questionsQuery } from "@/lib/queries/keys";
import { pickParam } from "@/lib/server/params";
import { prefetch, seedFirstPage } from "@/lib/server/prefetch";
import { QuestionsScreen } from "./questions-screen";

export const metadata: Metadata = { title: "질문", description: "아이 교육 고민을 묻고 전문가와 선배 부모에게 답을 받아요" };

export default function QuestionsPage({ searchParams }: PageProps<"/questions">) {
  return (
    <Suspense fallback={<ListScreenSkeleton title="질문" />}>
      <QuestionsWithData searchParams={searchParams} />
    </Suspense>
  );
}

async function QuestionsWithData({ searchParams }: Pick<PageProps<"/questions">, "searchParams">) {
  const params = await searchParams;
  const category = pickParam(params.category, keysOf(questionCategory));
  const status = pickParam(params.status, QUESTION_STATUS_FILTERS);
  const state = await prefetch([seedFirstPage(questionsQuery({ category, status }))]);
  return (
    <HydrationBoundary state={state}>
      <QuestionsScreen />
    </HydrationBoundary>
  );
}
