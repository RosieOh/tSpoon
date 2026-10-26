"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useOverlay } from "@/components/overlays/Overlays";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { BottomCTA } from "@/components/shell/Screen";
import { Button } from "@/components/ui/Button";
import { Chips } from "@/components/ui/Chips";
import { BareTextArea, BareTitleInput, Switch, TextField } from "@/components/ui/Field";
import { Segmented } from "@/components/ui/Segmented";
import { EmptyState } from "@/components/ui/States";
import { isApiError } from "@/lib/api";
import { eventType, keysOf } from "@/lib/labels";
import { useSaveEvent } from "@/lib/queries/events";
import { canCreateContent, useSession } from "@/lib/queries/session";
import type { EventType, TspoonEvent } from "@/lib/types";

// datetime-local 은 "YYYY-MM-DDTHH:mm" — 서버의 LocalDateTime 과 같은 현지 시각
const toInput = (local: string | null | undefined) => (local ? local.slice(0, 16) : "");
const toServer = (value: string) => (value ? `${value}:00` : null);

export function EventForm({ event }: { event?: TspoonEvent }) {
  const router = useRouter();
  const { user, isLoading } = useSession();
  const { toast } = useOverlay();
  const save = useSaveEvent(event?.id);
  const [type, setType] = useState<EventType | null>(event?.type ?? null);
  const [title, setTitle] = useState(event?.title ?? "");
  const [description, setDescription] = useState(event?.description ?? "");
  const [start, setStart] = useState(toInput(event?.startDate));
  const [end, setEnd] = useState(toInput(event?.endDate));
  const [regStart, setRegStart] = useState(toInput(event?.registrationStartDate));
  const [regEnd, setRegEnd] = useState(toInput(event?.registrationEndDate));
  const [place, setPlace] = useState<"offline" | "online">(event?.onlineLink && !event.location ? "online" : "offline");
  const [location, setLocation] = useState(event?.location ?? "");
  const [onlineLink, setOnlineLink] = useState(event?.onlineLink ?? "");
  const [limited, setLimited] = useState(event ? event.maxParticipants !== null : true);
  const [max, setMax] = useState(event?.maxParticipants ? String(event.maxParticipants) : "20");
  const [requirements, setRequirements] = useState(event?.requirements ?? "");
  const [contactInfo, setContactInfo] = useState(event?.contactInfo ?? "");
  const [errors, setErrors] = useState<Record<string, string>>({});

  const nav = (
    <NavBar
      left={<BackButton close fallback={event ? `/events/${event.id}` : "/events"} />}
      title={event ? "행사 고치기" : "행사 열기"}
      alwaysShowTitle
    />
  );

  if (!isLoading && !canCreateContent(user)) {
    return (
      <>
        {nav}
        <EmptyState emoji="lock" title="선생님과 전문가만 행사를 열 수 있어요" description="역할 변경은 관리자에게 문의해 주세요" />
      </>
    );
  }

  const valid =
    type !== null && title.trim() !== "" && description.trim() !== "" && start !== "" && end !== "" && (!limited || Number(max) > 0);

  const submit = () => {
    if (!valid || !type) return;
    setErrors({});
    save.mutate(
      {
        type,
        title: title.trim(),
        description: description.trim(),
        startDate: toServer(start)!,
        endDate: toServer(end)!,
        registrationStartDate: toServer(regStart),
        registrationEndDate: toServer(regEnd),
        location: place === "offline" ? location.trim() || null : null,
        onlineLink: place === "online" ? onlineLink.trim() || null : null,
        maxParticipants: limited ? Number(max) : null,
        requirements: requirements.trim() || null,
        contactInfo: contactInfo.trim() || null,
      },
      {
        onSuccess: (saved) => {
          toast(event ? "행사를 고쳤어요" : "행사를 열었어요");
          router.replace(`/events/${saved.id}`);
        },
        onError: (e) => {
          if (isApiError(e) && Object.keys(e.errors).length > 0) setErrors(e.errors);
          else toast(isApiError(e) ? e.userMessage : "저장하지 못했어요", "error");
        },
      },
    );
  };

  // 서버의 교차 검증(@AssertTrue) 오류는 메서드 이름으로 온다
  const periodError = errors.endAfterStart ?? errors.startDate ?? errors.endDate;
  const registrationError = errors.registrationPeriodValid;

  return (
    <>
      {nav}
      <div className="space-y-3 pt-2 pb-8">
        <div>
          <p className="mb-2 px-5 text-t6 font-semibold text-ink-2">어떤 행사인가요?</p>
          <Chips
            label="행사 종류"
            options={keysOf(eventType).map((t) => ({ value: t, label: eventType[t].label }))}
            value={type}
            onChange={setType}
          />
        </div>

        <div className="mx-3 rounded-section bg-surface p-5">
          <BareTitleInput value={title} onChange={(e) => setTitle(e.target.value)} placeholder="행사 이름" maxLength={255} aria-label="행사 이름" />
          {errors.title && <p className="mt-1 text-t7 text-danger-ink">{errors.title}</p>}
          <div className="my-4 h-px bg-line" />
          <BareTextArea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="무엇을 하는지, 누구에게 좋은지 적어주세요"
            aria-label="소개"
            className="min-h-40"
          />
        </div>

        <div className="mx-3 space-y-4 rounded-section bg-surface p-5">
          <h2 className="text-t4 font-bold text-ink">언제 열리나요?</h2>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <TextField label="시작" type="datetime-local" value={start} onChange={(e) => setStart(e.target.value)} />
            <TextField label="끝" type="datetime-local" value={end} min={start || undefined} onChange={(e) => setEnd(e.target.value)} />
          </div>
          {periodError && <p className="text-t7 text-danger-ink">{periodError}</p>}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <TextField label="신청 시작 (선택)" type="datetime-local" value={regStart} onChange={(e) => setRegStart(e.target.value)} />
            <TextField
              label="신청 마감 (선택)"
              type="datetime-local"
              value={regEnd}
              max={start || undefined}
              onChange={(e) => setRegEnd(e.target.value)}
            />
          </div>
          <p className={registrationError ? "text-t7 text-danger-ink" : "text-t7 text-ink-4"}>
            {registrationError ?? "비워두면 지금부터 행사 시작 전까지 신청을 받아요"}
          </p>
        </div>

        <div className="mx-3 space-y-4 rounded-section bg-surface p-5">
          <h2 className="text-t4 font-bold text-ink">어디서 열리나요?</h2>
          <Segmented
            label="진행 방식"
            options={[
              { value: "offline", label: "오프라인" },
              { value: "online", label: "온라인" },
            ]}
            value={place}
            onChange={setPlace}
          />
          {place === "offline" ? (
            <TextField value={location} onChange={(e) => setLocation(e.target.value)} placeholder="서울 마포구 티스푼 라운지 3층" aria-label="장소" />
          ) : (
            <TextField type="url" value={onlineLink} onChange={(e) => setOnlineLink(e.target.value)} placeholder="https://" aria-label="온라인 링크" />
          )}
        </div>

        <div className="mx-3 space-y-4 rounded-section bg-surface p-5">
          <Switch checked={limited} onChange={setLimited} label="정원 정하기" description="끄면 인원 제한 없이 받아요" />
          {limited && (
            <TextField
              label="정원"
              inputMode="numeric"
              value={max}
              onChange={(e) => setMax(e.target.value.replace(/\D/g, ""))}
              error={errors.maxParticipants}
              hint={event ? "지금 신청한 인원보다 적게 줄일 수는 없어요" : undefined}
            />
          )}
          <TextField label="참가 대상 (선택)" value={requirements} onChange={(e) => setRequirements(e.target.value)} placeholder="초등 1~3학년과 보호자" />
          <TextField label="문의 (선택)" value={contactInfo} onChange={(e) => setContactInfo(e.target.value)} placeholder="이메일이나 전화번호" />
        </div>
      </div>
      <BottomCTA>
        <Button block onClick={submit} disabled={!valid} loading={save.isPending}>
          {event ? "고친 내용 저장하기" : "행사 열기"}
        </Button>
      </BottomCTA>
    </>
  );
}
