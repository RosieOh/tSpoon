import { HydrationBoundary } from "@tanstack/react-query";
import type { Metadata } from "next";
import { Suspense } from "react";
import { DetailScreenSkeleton } from "@/components/shell/Screen";
import { eventRange } from "@/lib/format";
import { eventQuery } from "@/lib/queries/keys";
import { toId } from "@/lib/server/params";
import { getPublic, prefetch, seedOne } from "@/lib/server/prefetch";
import type { TspoonEvent } from "@/lib/types";
import { EventDetailScreen } from "./event-detail-screen";

export async function generateMetadata({ params }: PageProps<"/events/[id]">): Promise<Metadata> {
  const id = toId((await params).id);
  const event = id ? await getPublic<TspoonEvent>(eventQuery(id).path) : null;
  if (!event) return { title: "행사" };
  const when = eventRange(event.startDate, event.endDate);
  return {
    title: event.title,
    description: `${when} ${event.location ?? "온라인"}`,
    openGraph: { title: event.title, description: when },
  };
}

export default function EventDetailPage({ params }: PageProps<"/events/[id]">) {
  return (
    <Suspense fallback={<DetailScreenSkeleton fallback="/events" />}>
      <EventWithData params={params} />
    </Suspense>
  );
}

async function EventWithData({ params }: Pick<PageProps<"/events/[id]">, "params">) {
  const id = toId((await params).id);
  const state = await prefetch(id ? [seedOne(eventQuery(id))] : []);
  return (
    <HydrationBoundary state={state}>
      <EventDetailScreen />
    </HydrationBoundary>
  );
}
