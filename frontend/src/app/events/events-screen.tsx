"use client";

import { EventCard } from "@/components/domain/EventCard";
import { TabHeader, useQueryParam } from "@/components/shell/Screen";
import { Chips } from "@/components/ui/Chips";
import { EmptyState, ErrorState, LoadMore, SkeletonRows } from "@/components/ui/States";
import { eventType, keysOf } from "@/lib/labels";
import { useEvents } from "@/lib/queries/events";
import { flatten } from "@/lib/queries/paging";
import type { EventType } from "@/lib/types";

const TYPES = keysOf(eventType);

export function EventsScreen() {
  const [type, setType] = useQueryParam<EventType>("type", TYPES);
  const events = useEvents(type);
  const items = flatten(events.data);

  return (
    <>
      <TabHeader title="행사">
        <p className="mt-1 text-t5 text-ink-3">다가오는 순서로 보여드려요</p>
      </TabHeader>
      <Chips
        label="행사 종류"
        options={[{ value: null, label: "전체" }, ...TYPES.map((t) => ({ value: t, label: eventType[t].label }))]}
        value={type}
        onChange={setType}
      />
      <section aria-label="행사" className="mx-3 mt-3 overflow-hidden rounded-section bg-surface py-1">
        {events.isPending ? (
          <SkeletonRows count={5} />
        ) : events.isError ? (
          <ErrorState error={events.error} onRetry={() => events.refetch()} />
        ) : items.length === 0 ? (
          <EmptyState emoji="calendar" title="열리는 행사가 없어요" description="다른 종류를 골라보세요" />
        ) : (
          <ul className="divide-y divide-line">
            {items.map((event, index) => (
              <li key={event.id} className={index >= 4 ? "cv-row" : undefined}>
                <EventCard event={event} />
              </li>
            ))}
          </ul>
        )}
        <LoadMore {...events} />
      </section>
    </>
  );
}
