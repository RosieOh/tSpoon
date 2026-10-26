"use client";

import { BackButton, NavBar } from "@/components/shell/NavBar";
import { Badge } from "@/components/ui/Badge";
import { ButtonLink } from "@/components/ui/Button";
import { Emoji } from "@/components/ui/Emoji";
import { ListRow } from "@/components/ui/ListRow";
import { EmptyState, ErrorState, LoadMore, SkeletonRows } from "@/components/ui/States";
import { daysUntil, dDay, eventDateTime } from "@/lib/format";
import { registrationStatus } from "@/lib/labels";
import { useMyRegistrationList } from "@/lib/queries/events";
import { flatten } from "@/lib/queries/paging";
import type { Registration } from "@/lib/types";

function statusBadge(r: Registration) {
  if (r.status === "REGISTERED") {
    const days = daysUntil(r.eventStartDate);
    if (days >= 0) return <Badge tone="blue">{dDay(r.eventStartDate)}</Badge>;
    return <Badge>지난 행사</Badge>;
  }
  return <Badge tone={r.status === "ATTENDED" ? "green" : "grey"}>{registrationStatus[r.status]}</Badge>;
}

export default function MyEventsPage() {
  const registrations = useMyRegistrationList();
  const items = flatten(registrations.data);

  return (
    <>
      <NavBar left={<BackButton fallback="/me" />} title="신청한 행사" />
      <div className="px-5 pt-1 pb-4">
        <h1 className="text-t1 text-ink">신청한 행사</h1>
      </div>
      <section className="mx-3 rounded-section bg-surface py-2">
        {registrations.isPending ? (
          <SkeletonRows count={4} />
        ) : registrations.isError ? (
          <ErrorState error={registrations.error} onRetry={() => registrations.refetch()} />
        ) : items.length === 0 ? (
          <EmptyState
            emoji="ticket"
            title="신청한 행사가 없어요"
            description="아이와 함께할 행사를 찾아보세요"
            action={
              <ButtonLink href="/events" size="md" variant="weak">
                행사 둘러보기
              </ButtonLink>
            }
          />
        ) : (
          items.map((r) => (
            <ListRow
              key={r.id}
              href={`/events/${r.eventId}`}
              left={
                <span className="grid size-11 place-items-center rounded-[14px] bg-fill">
                  <Emoji name="calendar" size={28} />
                </span>
              }
              title={r.eventTitle}
              description={eventDateTime(r.eventStartDate)}
              right={statusBadge(r)}
            />
          ))
        )}
        <LoadMore {...registrations} />
      </section>
    </>
  );
}
