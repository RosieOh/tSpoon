import { HydrationBoundary } from "@tanstack/react-query";
import { Suspense } from "react";
import { boardsQuery, contentsQuery, eventsQuery, HOME, questionsQuery } from "@/lib/queries/keys";
import { prefetch, seedFirstPage } from "@/lib/server/prefetch";
import { HomeScreen, HomeSkeleton } from "./home-screen";

// 정적 셸(뼈대)은 바로 보내고, 공개 목록을 담은 화면을 같은 응답 안에서 이어서 보낸다
export default function HomePage() {
  return (
    <Suspense fallback={<HomeSkeleton />}>
      <HomeWithData />
    </Suspense>
  );
}

async function HomeWithData() {
  const state = await prefetch([
    seedFirstPage(contentsQuery({}, HOME.contents)),
    seedFirstPage(questionsQuery({ status: "OPEN" }, HOME.openQuestions)),
    seedFirstPage(eventsQuery(null, HOME.events)),
    seedFirstPage(boardsQuery(null, HOME.boards)),
  ]);
  return (
    <HydrationBoundary state={state}>
      <HomeScreen />
    </HydrationBoundary>
  );
}
