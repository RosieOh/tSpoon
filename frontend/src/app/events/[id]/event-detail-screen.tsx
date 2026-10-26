"use client";

import { Ban, CalendarClock, CircleCheck, Clock, Link2, MapPin, Pencil, Phone, UserRound, Users } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useParams, useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import { CoverArt } from "@/components/domain/CoverArt";
import { SeatsBar, seatState } from "@/components/domain/EventCard";
import { useOverlay } from "@/components/overlays/Overlays";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { BottomCTA, DetailScreenSkeleton } from "@/components/shell/Screen";
import { MoreMenu, type SheetAction } from "@/components/ui/ActionSheet";
import { Avatar } from "@/components/ui/Avatar";
import { Badge } from "@/components/ui/Badge";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { Button } from "@/components/ui/Button";
import { TextArea, TextField } from "@/components/ui/Field";
import { CheckMark } from "@/components/ui/Result";
import { EmptyState, ErrorState, SkeletonRows } from "@/components/ui/States";
import { isApiError } from "@/lib/api";
import { dDay, eventDate, eventDateTime, eventRange, isPast } from "@/lib/format";
import { eventStatus, eventType, registrationStatus } from "@/lib/labels";
import {
  useCancelEvent,
  useCancelRegistration,
  useEvent,
  useEventRegistrations,
  useMyRegistrations,
  useRegister,
} from "@/lib/queries/events";
import { useSession } from "@/lib/queries/session";
import type { TspoonEvent } from "@/lib/types";

export function EventDetailScreen() {
  const { id } = useParams<{ id: string }>();
  const event = useEvent(Number(id));

  if (event.isPending) return <DetailScreenSkeleton fallback="/events" />;
  if (event.isError) {
    return (
      <>
        <NavBar left={<BackButton fallback="/events" />} />
        <ErrorState error={event.error} onRetry={() => event.refetch()} />
      </>
    );
  }
  return <EventDetail event={event.data} />;
}

function registrationPeriod(event: TspoonEvent) {
  const { registrationStartDate: start, registrationEndDate: end } = event;
  if (start && end) return `${eventDateTime(start)} ~ ${eventDateTime(end)}`;
  if (end) return `${eventDateTime(end)}까지`;
  if (start) return `${eventDateTime(start)}부터 행사 시작 전까지`;
  return "행사 시작 전까지";
}

function EventDetail({ event }: { event: TspoonEvent }) {
  const router = useRouter();
  const { user } = useSession();
  const { confirm, toast, requireAuth } = useOverlay();
  const mine = useMyRegistrations();
  const cancelEvent = useCancelEvent(event.id);
  const cancelRegistration = useCancelRegistration(event.id);
  const [applyOpen, setApplyOpen] = useState(false);
  const [applicantsOpen, setApplicantsOpen] = useState(false);
  const [done, setDone] = useState(false);

  const visual = eventType[event.type];
  const state = seatState(event);
  const registration = mine.data?.find((r) => r.eventId === event.id && r.status === "REGISTERED");
  const canManage = user !== null && (user.id === event.organizerId || user.role === "ADMIN");
  const started = isPast(event.startDate);
  const notOpenYet = event.registrationStartDate !== null && !isPast(event.registrationStartDate);
  const deadlinePassed = event.registrationEndDate !== null && isPast(event.registrationEndDate);

  const actions: SheetAction[] = [];
  if (canManage && event.status !== "CANCELLED") {
    actions.push(
      { label: "수정하기", icon: Pencil, onSelect: () => router.push(`/events/${event.id}/edit`) },
      { label: "신청자 보기", icon: Users, onSelect: () => setApplicantsOpen(true) },
      {
        label: "행사 취소하기",
        icon: Ban,
        danger: true,
        onSelect: async () => {
          const ok = await confirm({
            title: "행사를 취소할까요?",
            description: "신청한 분들은 더 이상 참여할 수 없어요. 되돌릴 수 없어요.",
            confirmLabel: "행사 취소하기",
            cancelLabel: "그대로 두기",
            danger: true,
          });
          if (ok) {
            cancelEvent.mutate(undefined, {
              onSuccess: () => toast("행사를 취소했어요"),
              onError: (e) => toast(isApiError(e) ? e.userMessage : "취소하지 못했어요", "error"),
            });
          }
        },
      },
    );
  }

  let cta: ReactNode;
  if (registration) {
    cta = (
      <>
        <p className="mb-3 flex items-center justify-center gap-1.5 text-t6 font-semibold text-positive">
          <CircleCheck className="size-[18px]" aria-hidden />
          {started ? "신청한 행사예요" : `신청을 마쳤어요. ${eventDate(event.startDate)}에 만나요`}
        </p>
        <Button
          variant="surface"
          block
          disabled={started}
          loading={cancelRegistration.isPending}
          onClick={async () => {
            const ok = await confirm({
              title: "신청을 취소할까요?",
              description: "자리가 다른 분께 돌아가요. 다시 신청하려면 남은 자리가 있어야 해요.",
              confirmLabel: "신청 취소하기",
              cancelLabel: "그대로 두기",
              danger: true,
            });
            if (ok) {
              cancelRegistration.mutate(undefined, {
                onSuccess: () => toast("신청을 취소했어요"),
                onError: (e) => toast(isApiError(e) ? e.userMessage : "취소하지 못했어요", "error"),
              });
            }
          }}
        >
          신청 취소하기
        </Button>
      </>
    );
  } else if (state.closed) {
    cta = (
      <Button block variant="surface" disabled>
        {event.status === "CANCELLED" ? "취소된 행사예요" : state.label}
      </Button>
    );
  } else if (notOpenYet && event.registrationStartDate) {
    cta = (
      <Button block variant="surface" disabled>
        {eventDateTime(event.registrationStartDate)}부터 신청할 수 있어요
      </Button>
    );
  } else if (deadlinePassed || started) {
    cta = (
      <Button block variant="surface" disabled>
        신청이 마감됐어요
      </Button>
    );
  } else {
    cta = (
      <Button block onClick={() => requireAuth("행사를 신청하려면") && setApplyOpen(true)}>
        신청하기
      </Button>
    );
  }

  return (
    <>
      <NavBar overlay left={<BackButton fallback="/events" />} title={event.title} right={<MoreMenu actions={actions} />} />
      <div className="relative h-[280px] overflow-hidden lg:mx-3 lg:mt-3 lg:rounded-t-[28px]">
        <CoverArt tint={visual.tint} emoji={visual.emoji} label={visual.label} imageUrl={event.imageUrl} variant="banner" eager />
        {event.status === "ACTIVE" && !started && (
          <span className="glass glass-on-cover absolute bottom-[104px] left-5 rounded-full px-3 py-1.5 text-t6 font-bold [--glass-blur:10px]">
            <span className="tabular relative z-[2]">{dDay(event.startDate)}</span>
          </span>
        )}
      </div>

      <article className="relative -mt-7 rounded-[28px] rounded-b-section bg-surface px-5 pt-7 pb-6 lg:mx-3">
        <div className="flex flex-wrap items-center gap-1.5">
          <Badge tint={visual.tint}>{visual.label}</Badge>
          {event.status !== "ACTIVE" && (
            <Badge tone={event.status === "CANCELLED" ? "red" : "grey"}>{eventStatus[event.status]}</Badge>
          )}
          {registration && <Badge tone="green">{registrationStatus.REGISTERED}</Badge>}
        </div>
        <h1 className="mt-3 text-t1 text-ink">{event.title}</h1>

        {event.maxParticipants !== null && !state.closed && (
          <div className="mt-5 rounded-[18px] bg-fill p-4">
            <p className="tabular text-t3 font-bold text-ink">{state.label}</p>
            <SeatsBar event={event} showState={false} className="mt-2.5" />
          </div>
        )}

        <dl className="mt-5 space-y-4">
          <Info icon={<Clock />} label="일정">
            {eventRange(event.startDate, event.endDate)}
          </Info>
          <Info icon={<CalendarClock />} label="신청 기간">
            {registrationPeriod(event)}
          </Info>
          {event.location && (
            <Info icon={<MapPin />} label="장소">
              {event.location}
            </Info>
          )}
          {event.onlineLink && (
            <Info icon={<Link2 />} label="온라인">
              <a href={event.onlineLink} target="_blank" rel="noreferrer" className="break-all text-primary-ink underline-offset-2 hover:underline">
                {event.onlineLink}
              </a>
            </Info>
          )}
          {event.requirements && (
            <Info icon={<UserRound />} label="대상">
              {event.requirements}
            </Info>
          )}
          {event.contactInfo && (
            <Info icon={<Phone />} label="문의">
              {event.contactInfo}
            </Info>
          )}
        </dl>
      </article>

      <section className="mx-3 mt-3 rounded-section bg-surface px-5 py-6" aria-labelledby="event-about">
        <h2 id="event-about" className="text-t4 font-bold text-ink">
          이런 행사예요
        </h2>
        <p className="mt-3 text-body whitespace-pre-wrap text-ink-2">{event.description}</p>
        <div className="mt-6 flex items-center gap-3 border-t border-line pt-5">
          <Avatar name={event.organizerName} size={40} />
          <div>
            <p className="text-t6 font-semibold text-ink">{event.organizerName}</p>
            <p className="text-t7 text-ink-4">주최</p>
          </div>
        </div>
      </section>
      <div aria-hidden className="h-16" />

      <BottomCTA>{cta}</BottomCTA>

      <ApplySheet event={event} open={applyOpen} onClose={() => setApplyOpen(false)} onDone={() => setDone(true)} />
      {canManage && <ApplicantsSheet eventId={event.id} open={applicantsOpen} onClose={() => setApplicantsOpen(false)} />}
      <AnimatePresence>{done && <DoneScreen event={event} onClose={() => setDone(false)} />}</AnimatePresence>
    </>
  );
}

function Info({ icon, label, children }: { icon: ReactNode; label: string; children: ReactNode }) {
  return (
    <div className="flex gap-3">
      <dt className="flex w-[84px] shrink-0 items-center gap-2 self-start text-t6 text-ink-3 [&_svg]:size-[18px] [&_svg]:shrink-0">
        {icon}
        {label}
      </dt>
      <dd className="min-w-0 flex-1 text-t6 font-medium text-ink-2">{children}</dd>
    </div>
  );
}

function ApplySheet({
  event,
  open,
  onClose,
  onDone,
}: {
  event: TspoonEvent;
  open: boolean;
  onClose: () => void;
  onDone: () => void;
}) {
  const { user } = useSession();
  const { toast } = useOverlay();
  const register = useRegister(event.id);
  const [notes, setNotes] = useState("");
  const [contact, setContact] = useState("");

  return (
    <BottomSheet open={open} onClose={onClose} title="이 행사에 신청할게요" description={eventRange(event.startDate, event.endDate)}>
      <div className="space-y-4">
        <TextField
          label="연락처"
          value={contact}
          onChange={(e) => setContact(e.target.value)}
          placeholder={user?.phone ?? "010-0000-0000"}
          maxLength={255}
          hint="비워두면 회원 정보의 연락처로 안내해요"
        />
        <TextArea
          label="주최자에게 남길 말"
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder="함께 오는 아이의 나이, 필요한 도움 등"
          maxLength={255}
          className="min-h-24"
        />
      </div>
      <Button
        block
        className="mt-6"
        loading={register.isPending}
        onClick={() =>
          register.mutate(
            { notes: notes.trim() || undefined, contactInfo: contact.trim() || undefined },
            {
              onSuccess: () => {
                setNotes("");
                setContact("");
                onClose();
                onDone();
              },
              onError: (e) => toast(isApiError(e) ? e.userMessage : "신청하지 못했어요", "error"),
            },
          )
        }
      >
        신청하기
      </Button>
    </BottomSheet>
  );
}

function ApplicantsSheet({ eventId, open, onClose }: { eventId: number; open: boolean; onClose: () => void }) {
  const list = useEventRegistrations(eventId, open);
  const active = list.data?.filter((r) => r.status === "REGISTERED") ?? [];
  return (
    <BottomSheet open={open} onClose={onClose} title="신청한 분들" description={list.data ? `${active.length}명이 신청했어요` : undefined}>
      {list.isPending ? (
        <SkeletonRows count={3} />
      ) : list.isError ? (
        <ErrorState error={list.error} onRetry={() => list.refetch()} />
      ) : active.length === 0 ? (
        <EmptyState emoji="ticket" title="아직 신청한 분이 없어요" className="py-8" />
      ) : (
        <ul className="-mx-1">
          {active.map((r) => (
            <li key={r.id} className="flex items-start gap-3 px-1 py-3">
              <Avatar name={r.userName} size={36} />
              <div className="min-w-0 flex-1">
                <p className="text-t5 font-semibold text-ink">{r.userName}</p>
                {r.contactInfo && <p className="text-t7 text-ink-3">{r.contactInfo}</p>}
                {r.notes && <p className="mt-1 rounded-[10px] bg-fill px-3 py-2 text-t7 text-ink-2">{r.notes}</p>}
              </div>
            </li>
          ))}
        </ul>
      )}
    </BottomSheet>
  );
}

/** 토스식 완료 화면 — 체크가 그려지고 한 줄로 결과를 알려준다 */
function DoneScreen({ event, onClose }: { event: TspoonEvent; onClose: () => void }) {
  return (
    <motion.div
      role="dialog"
      aria-modal="true"
      aria-labelledby="done-title"
      className="fixed inset-0 z-50 flex flex-col bg-bg"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
    >
      <div className="mx-auto flex w-full max-w-[640px] flex-1 flex-col items-center justify-center px-6 text-center">
        <CheckMark />
        <h2 id="done-title" className="mt-6 text-t1 text-ink">
          신청을 마쳤어요
        </h2>
        <p className="mt-2 text-t5 text-ink-3">{event.title}</p>
        <p className="tabular mt-0.5 text-t5 font-semibold text-ink-2">{eventRange(event.startDate, event.endDate)}</p>
      </div>
      <div className="mx-auto w-full max-w-[640px] px-5 pb-[max(16px,env(safe-area-inset-bottom))]">
        <Button block onClick={onClose} autoFocus>
          확인
        </Button>
      </div>
    </motion.div>
  );
}
