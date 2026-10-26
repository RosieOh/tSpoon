import clsx from "clsx";
import { Clock, MapPin, Video } from "lucide-react";
import Link from "next/link";
import type { CSSProperties } from "react";
import { Badge } from "@/components/ui/Badge";
import { Emoji } from "@/components/ui/Emoji";
import { daysUntil, dDay, eventDate, eventDateParts, eventTimeRange } from "@/lib/format";
import { eventStatus, eventType } from "@/lib/labels";
import type { TspoonEvent } from "@/lib/types";

/** 신청 가능 여부 — 목록과 상세가 같은 규칙을 쓴다 */
export function seatState(event: TspoonEvent) {
  if (event.status === "CANCELLED") return { closed: true, label: "취소된 행사" };
  if (event.status === "COMPLETED" || daysUntil(event.startDate) < 0) return { closed: true, label: "종료된 행사" };
  if (event.status === "FULL" || event.remainingSeats === 0) return { closed: true, label: "자리가 모두 찼어요" };
  if (event.remainingSeats === null) return { closed: false, label: "인원 제한 없음" };
  return { closed: false, label: `${event.remainingSeats}자리 남았어요` };
}

export function SeatsBar({
  event,
  className,
  showState = true,
}: {
  event: TspoonEvent;
  className?: string;
  /** 남은 자리 문구를 막대 아래에 함께 보여줄지 (상세에서는 위에 크게 보여주므로 끈다) */
  showState?: boolean;
}) {
  if (event.maxParticipants === null) return null;
  const ratio = Math.min(1, event.currentParticipants / event.maxParticipants);
  const almost = event.remainingSeats !== null && event.remainingSeats <= Math.max(2, event.maxParticipants * 0.2);
  return (
    <div className={className}>
      <div
        className="h-1.5 overflow-hidden rounded-full bg-fill"
        role="meter"
        aria-valuemin={0}
        aria-valuemax={event.maxParticipants}
        aria-valuenow={event.currentParticipants}
        aria-label="신청 인원"
      >
        <div
          className={clsx("h-full rounded-full transition-[width] duration-500", almost ? "bg-danger" : "bg-primary")}
          style={{ width: `${Math.max(ratio * 100, 4)}%` }}
        />
      </div>
      <p className="tabular mt-1.5 flex justify-between text-t8 text-ink-4">
        {showState ? (
          <span className={clsx(almost && "font-semibold text-danger-ink")}>{seatState(event).label}</span>
        ) : (
          <span>{event.currentParticipants}명 신청</span>
        )}
        <span>{showState ? `${event.currentParticipants}/${event.maxParticipants}명` : `정원 ${event.maxParticipants}명`}</span>
      </p>
    </div>
  );
}

function DateBlock({ event }: { event: TspoonEvent }) {
  const { month, day, weekday } = eventDateParts(event.startDate);
  const visual = eventType[event.type];
  return (
    <div
      className="tint-bg tint-ink flex w-[58px] shrink-0 flex-col items-center self-start rounded-[16px] py-2.5"
      style={{ "--tint": visual.tint } as CSSProperties}
      aria-hidden
    >
      <span className="text-t8 font-semibold">{month}월</span>
      <span className="tabular text-t2 leading-tight">{day}</span>
      <span className="text-t8 font-medium opacity-80">{weekday}</span>
    </div>
  );
}

export function EventCard({ event }: { event: TspoonEvent }) {
  const visual = eventType[event.type];
  const state = seatState(event);
  const days = daysUntil(event.startDate);
  return (
    <Link href={`/events/${event.id}`} className="press-row flex gap-4 px-5 py-4">
      <DateBlock event={event} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-1.5">
          <Badge tint={visual.tint}>{visual.label}</Badge>
          {event.status !== "ACTIVE" ? (
            <Badge tone={event.status === "CANCELLED" ? "red" : "grey"}>{eventStatus[event.status]}</Badge>
          ) : (
            days >= 0 && days <= 7 && <Badge tone="blue">{dDay(event.startDate)}</Badge>
          )}
        </div>
        <p className="mt-1.5 line-clamp-2 text-t4 font-semibold text-ink">{event.title}</p>
        <p className="mt-1 flex items-center gap-1 text-t7 text-ink-3">
          <Clock className="size-3.5 shrink-0" aria-hidden />
          <span className="truncate">
            {eventDate(event.startDate)} {eventTimeRange(event.startDate, event.endDate)}
          </span>
        </p>
        <p className="mt-0.5 flex items-center gap-1 text-t7 text-ink-3">
          {event.location ? (
            <MapPin className="size-3.5 shrink-0" aria-hidden />
          ) : (
            <Video className="size-3.5 shrink-0" aria-hidden />
          )}
          <span className="truncate">{event.location ?? "온라인"}</span>
        </p>
        {!state.closed && <SeatsBar event={event} className="mt-3" />}
      </div>
    </Link>
  );
}

/** 홈의 가로 스크롤 카드 */
export function EventMiniCard({ event }: { event: TspoonEvent }) {
  const visual = eventType[event.type];
  return (
    <Link
      href={`/events/${event.id}`}
      className="press tint-bg relative flex w-[208px] shrink-0 flex-col overflow-hidden rounded-[20px] p-4"
      style={{ "--tint": visual.tint } as CSSProperties}
    >
      <div className="flex items-start justify-between">
        <Emoji name={visual.emoji} size={44} />
        <span className="glass rounded-full px-2.5 py-1 text-t8 font-bold text-ink [--glass-blur:8px]">
          <span className="relative z-[2] tabular">{dDay(event.startDate)}</span>
        </span>
      </div>
      <p className="mt-3 line-clamp-2 min-h-[2lh] text-t5 font-semibold text-ink">{event.title}</p>
      <p className="mt-1 text-t7 text-ink-3">{eventDate(event.startDate)}</p>
    </Link>
  );
}
