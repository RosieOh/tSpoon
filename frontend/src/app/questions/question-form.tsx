"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useOverlay } from "@/components/overlays/Overlays";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { BottomCTA } from "@/components/shell/Screen";
import { Button } from "@/components/ui/Button";
import { Chips } from "@/components/ui/Chips";
import { BareTextArea, BareTitleInput, TextField } from "@/components/ui/Field";
import { Segmented } from "@/components/ui/Segmented";
import { isApiError } from "@/lib/api";
import { keysOf, priorityLabel, questionCategory } from "@/lib/labels";
import { useSaveQuestion } from "@/lib/queries/questions";
import type { Priority, Question, QuestionCategory } from "@/lib/types";

const PRIORITIES: Priority[] = ["LOW", "NORMAL", "HIGH", "URGENT"];

export function QuestionForm({ question }: { question?: Question }) {
  const router = useRouter();
  const { toast } = useOverlay();
  const save = useSaveQuestion(question?.id);
  const [category, setCategory] = useState<QuestionCategory | null>(question?.category ?? null);
  const [priority, setPriority] = useState<Priority>(question?.priority ?? "NORMAL");
  const [title, setTitle] = useState(question?.title ?? "");
  const [content, setContent] = useState(question?.content ?? "");
  const [tags, setTags] = useState(question?.tags ?? "");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const valid = category !== null && title.trim() !== "" && content.trim() !== "";

  const submit = () => {
    if (!valid || !category) return;
    setErrors({});
    save.mutate(
      { category, priority, title: title.trim(), content: content.trim(), tags: tags.trim() || null },
      {
        onSuccess: (saved) => {
          toast(question ? "질문을 고쳤어요" : "질문을 올렸어요");
          router.replace(`/questions/${saved.id}`);
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
        left={<BackButton close fallback={question ? `/questions/${question.id}` : "/questions"} />}
        title={question ? "질문 고치기" : "질문하기"}
        alwaysShowTitle
      />
      <div className="space-y-3 pt-2 pb-8">
        <div>
          <p className="mb-2 px-5 text-t6 font-semibold text-ink-2">어떤 고민인가요?</p>
          <Chips
            label="분야"
            options={keysOf(questionCategory).map((c) => ({ value: c, label: questionCategory[c].label }))}
            value={category}
            onChange={setCategory}
          />
        </div>
        <div className="mx-3 rounded-section bg-surface p-5">
          <BareTitleInput
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="궁금한 점을 한 문장으로 적어주세요"
            maxLength={255}
            aria-label="제목"
          />
          {errors.title && <p className="mt-1 text-t7 text-danger-ink">{errors.title}</p>}
          <div className="my-4 h-px bg-line" />
          <BareTextArea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder={"아이의 나이, 상황, 지금까지 해본 방법을 함께 적으면\n더 꼭 맞는 답을 받을 수 있어요."}
            aria-label="내용"
          />
          {errors.content && <p className="mt-1 text-t7 text-danger-ink">{errors.content}</p>}
        </div>
        <div className="mx-3 space-y-5 rounded-section bg-surface p-5">
          <div>
            <p className="mb-2 text-t7 font-medium text-ink-3">얼마나 급한가요?</p>
            <Segmented
              label="급한 정도"
              options={PRIORITIES.map((p) => ({ value: p, label: priorityLabel[p] }))}
              value={priority}
              onChange={setPriority}
            />
          </div>
          <TextField label="태그" value={tags} onChange={(e) => setTags(e.target.value)} placeholder="7살, 유치원" />
        </div>
      </div>
      <BottomCTA>
        <Button block onClick={submit} disabled={!valid} loading={save.isPending}>
          {question ? "고친 내용 저장하기" : "질문 올리기"}
        </Button>
      </BottomCTA>
    </>
  );
}
