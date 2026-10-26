import { HydrationBoundary } from "@tanstack/react-query";
import type { Metadata } from "next";
import { Suspense } from "react";
import { ListScreenSkeleton } from "@/components/shell/Screen";
import { eventType, keysOf } from "@/lib/labels";
import { eventsQuery } from "@/lib/queries/keys";
import { pickParam } from "@/lib/server/params";
import { prefetch, seedFirstPage } from "@/lib/server/prefetch";
import { EventsScreen } from "./events-screen";

export const metadata: Metadata = { title: "행사", description: "아이와 함께하는 세미나·워크숍·캠프" };

export default function EventsPage({ searchParams }: PageProps<"/events">) {
  return (
    <Suspense fallback={<ListScreenSkeleton title="행사" />}>
      <EventsWithData searchParams={searchParams} />
    </Suspense>
  );
}

async function EventsWithData({ searchParams }: Pick<PageProps<"/events">, "searchParams">) {
  const type = pickParam((await searchParams).type, keysOf(eventType));
  const state = await prefetch([seedFirstPage(eventsQuery(type))]);
  return (
    <HydrationBoundary state={state}>
      <EventsScreen />
    </HydrationBoundary>
  );
}
