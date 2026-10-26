"use client";

import { QuestionRow } from "@/components/domain/QuestionRow";
import { TabHeader, useQueryParam } from "@/components/shell/Screen";
import { Chips } from "@/components/ui/Chips";
import { Segmented } from "@/components/ui/Segmented";
import { EmptyState, ErrorState, LoadMore, SkeletonRows } from "@/components/ui/States";
import { keysOf, questionCategory } from "@/lib/labels";
import { QUESTION_STATUS_FILTERS } from "@/lib/queries/keys";
import { flatten } from "@/lib/queries/paging";
import { useQuestions } from "@/lib/queries/questions";
import type { QuestionCategory } from "@/lib/types";

const CATEGORIES = keysOf(questionCategory);
type StatusFilter = (typeof QUESTION_STATUS_FILTERS)[number] | "ALL";

export function QuestionsScreen() {
  const [category, setCategory] = useQueryParam<QuestionCategory>("category", CATEGORIES);
  const [status, setStatus] = useQueryParam<(typeof QUESTION_STATUS_FILTERS)[number]>("status", QUESTION_STATUS_FILTERS);
  const questions = useQuestions({ category, status });
  const items = flatten(questions.data);

  return (
    <>
      <TabHeader title="질문">
        <p className="mt-1 text-t5 text-ink-3">전문가와 선배 부모가 답해요</p>
      </TabHeader>
      <div className="px-5 pb-3">
        <Segmented<StatusFilter>
          label="답변 상태"
          options={[
            { value: "ALL", label: "전체" },
            { value: "OPEN", label: "답변 기다리는 중" },
            { value: "ANSWERED", label: "답변 완료" },
          ]}
          value={status ?? "ALL"}
          onChange={(v) => setStatus(v === "ALL" ? null : v)}
        />
      </div>
      <Chips
        label="분야"
        options={[{ value: null, label: "전체" }, ...CATEGORIES.map((c) => ({ value: c, label: questionCategory[c].label }))]}
        value={category}
        onChange={setCategory}
      />
      <section aria-label="질문" className="mx-3 mt-3 overflow-hidden rounded-section bg-surface py-1">
        {questions.isPending ? (
          <SkeletonRows count={6} icon={false} />
        ) : questions.isError ? (
          <ErrorState error={questions.error} onRetry={() => questions.refetch()} />
        ) : items.length === 0 ? (
          <EmptyState
            emoji="raising-hand"
            title={status === "OPEN" ? "기다리는 질문이 없어요" : "아직 질문이 없어요"}
            description="궁금한 게 있다면 먼저 물어보세요"
          />
        ) : (
          <ul className="divide-y divide-line">
            {items.map((q, index) => (
              <li key={q.id} className={index >= 5 ? "cv-row" : undefined}>
                <QuestionRow question={q} />
              </li>
            ))}
          </ul>
        )}
        <LoadMore {...questions} />
      </section>
    </>
  );
}
