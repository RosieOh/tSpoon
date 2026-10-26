"use client";

import { X } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState, useSyncExternalStore } from "react";
import { useOverlay } from "@/components/overlays/Overlays";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { BottomCTA } from "@/components/shell/Screen";
import { Button } from "@/components/ui/Button";
import { Chips } from "@/components/ui/Chips";
import { TextArea, TextField } from "@/components/ui/Field";
import { isApiError } from "@/lib/api";
import { gradeLabel, keysOf } from "@/lib/labels";
import { useDeleteChild, useSaveChild } from "@/lib/queries/session";
import type { Child, Grade } from "@/lib/types";

const MAX_INTERESTS = 10;

function today() {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

const noopSubscribe = () => () => {};

// 오늘 날짜는 방문할 때마다 달라지므로 프리렌더에 굳히지 않고 브라우저에서만 읽는다
function useToday() {
  return useSyncExternalStore(noopSubscribe, today, () => undefined);
}

export function ChildForm({ child }: { child?: Child }) {
  const router = useRouter();
  const { toast, confirm } = useOverlay();
  const save = useSaveChild(child?.id);
  const remove = useDeleteChild();
  const [name, setName] = useState(child?.name ?? "");
  const [grade, setGrade] = useState<Grade | null>(child?.grade ?? null);
  const [birthDate, setBirthDate] = useState(child?.birthDate ?? "");
  const [school, setSchool] = useState(child?.school ?? "");
  const [interests, setInterests] = useState<string[]>(child?.interests ?? []);
  const [interestDraft, setInterestDraft] = useState("");
  const [notes, setNotes] = useState(child?.notes ?? "");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const maxBirthDate = useToday();
  const valid = name.trim() !== "" && grade !== null;

  const addInterest = () => {
    const value = interestDraft.trim().slice(0, 30);
    if (!value || interests.includes(value) || interests.length >= MAX_INTERESTS) return;
    setInterests([...interests, value]);
    setInterestDraft("");
  };

  const submit = () => {
    if (!valid || !grade) return;
    setErrors({});
    // 입력 중이던 관심사도 함께 저장한다
    const pending = interestDraft.trim().slice(0, 30);
    const allInterests =
      pending && !interests.includes(pending) && interests.length < MAX_INTERESTS ? [...interests, pending] : interests;
    save.mutate(
      {
        name: name.trim(),
        grade,
        birthDate: birthDate || null,
        school: school.trim() || null,
        interests: allInterests,
        notes: notes.trim() || null,
      },
      {
        onSuccess: () => {
          toast(child ? "아이 정보를 고쳤어요" : `${name.trim()}의 정보를 등록했어요`);
          router.back();
        },
        onError: (e) => {
          if (isApiError(e) && Object.keys(e.errors).length > 0) setErrors(e.errors);
          else toast(isApiError(e) ? e.userMessage : "저장하지 못했어요", "error");
        },
      },
    );
  };

  return (
    <>
      <NavBar
        left={<BackButton close fallback="/me" />}
        title={child ? "아이 정보 고치기" : "아이 등록하기"}
        alwaysShowTitle
      />
      <form
        id="child-form"
        className="space-y-3 pt-2 pb-8"
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
      >
        <div className="mx-3 space-y-5 rounded-section bg-surface p-5">
          <TextField
            label="이름"
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={50}
            placeholder="아이 이름이나 애칭"
            error={errors.name}
            autoFocus={!child}
          />
          <TextField
            label="생일 (선택)"
            type="date"
            value={birthDate}
            max={maxBirthDate}
            onChange={(e) => setBirthDate(e.target.value)}
            error={errors.birthDate}
          />
        </div>

        <div className="rounded-section">
          <p className="mt-3 mb-2 px-5 text-t6 font-semibold text-ink-2">학년</p>
          <Chips
            label="학년"
            options={keysOf(gradeLabel).map((g) => ({ value: g, label: gradeLabel[g] }))}
            value={grade}
            onChange={setGrade}
          />
          {errors.grade && <p className="mt-1 px-5 text-t7 text-danger-ink">{errors.grade}</p>}
        </div>

        <div className="mx-3 space-y-5 rounded-section bg-surface p-5">
          <TextField label="학교·기관 (선택)" value={school} onChange={(e) => setSchool(e.target.value)} maxLength={100} placeholder="서울한빛초등학교" />

          <div>
            <TextField
              label={`관심사 (${interests.length}/${MAX_INTERESTS})`}
              value={interestDraft}
              onChange={(e) => setInterestDraft(e.target.value)}
              onKeyDown={(e) => {
                // 한글 조합 중 Enter 는 글자 확정이므로 넘긴다
                if (e.key === "Enter" && !e.nativeEvent.isComposing) {
                  e.preventDefault();
                  addInterest();
                }
              }}
              onBlur={addInterest}
              maxLength={30}
              placeholder="공룡, 그림 그리기"
              hint="입력하고 Enter 를 누르면 추가돼요"
              disabled={interests.length >= MAX_INTERESTS}
              error={errors.interests}
            />
            {interests.length > 0 && (
              <ul className="mt-3 flex flex-wrap gap-1.5" aria-label="관심사">
                {interests.map((interest) => (
                  <li key={interest} className="flex items-center gap-1 rounded-full bg-primary-weak py-1 pr-1.5 pl-3 text-t6 font-medium text-primary-weak-ink">
                    {interest}
                    <button
                      type="button"
                      aria-label={`${interest} 빼기`}
                      className="press grid size-5 place-items-center rounded-full"
                      onClick={() => setInterests(interests.filter((i) => i !== interest))}
                    >
                      <X className="size-3.5" aria-hidden />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <TextArea
            label="메모 (선택)"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            maxLength={1000}
            placeholder="알레르기, 좋아하는 것, 선생님께 알려두고 싶은 것"
            className="min-h-24"
            hint="나만 볼 수 있어요"
          />
        </div>

        {child && (
          <div className="flex justify-center pt-2">
            <button
              type="button"
              className="press rounded-lg px-3 py-2 text-t6 font-medium text-danger-ink"
              onClick={async () => {
                const ok = await confirm({
                  title: `${child.name}의 정보를 지울까요?`,
                  description: "지운 정보는 되돌릴 수 없어요.",
                  confirmLabel: "지우기",
                  danger: true,
                });
                if (!ok) return;
                remove.mutate(child.id, {
                  onSuccess: () => {
                    toast("아이 정보를 지웠어요");
                    router.back();
                  },
                  onError: (e) => toast(isApiError(e) ? e.userMessage : "지우지 못했어요", "error"),
                });
              }}
            >
              아이 정보 지우기
            </button>
          </div>
        )}
      </form>
      <BottomCTA>
        <Button block type="submit" form="child-form" disabled={!valid} loading={save.isPending}>
          {child ? "고친 내용 저장하기" : "등록하기"}
        </Button>
      </BottomCTA>
    </>
  );
}
