"use client";

import clsx from "clsx";
import { Check, CircleSlash, Pencil, Trash } from "lucide-react";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { useOverlay } from "@/components/overlays/Overlays";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { BottomCTA, DetailScreenSkeleton } from "@/components/shell/Screen";
import { RelativeTime } from "@/components/ui/RelativeTime";
import { MoreMenu, type SheetAction } from "@/components/ui/ActionSheet";
import { Avatar } from "@/components/ui/Avatar";
import { Badge } from "@/components/ui/Badge";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { Button } from "@/components/ui/Button";
import { Emoji } from "@/components/ui/Emoji";
import { TextArea } from "@/components/ui/Field";
import { EmptyState, ErrorState } from "@/components/ui/States";
import { isApiError } from "@/lib/api";
import { answerType, HUE, questionCategory, questionStatus, roleLabel, splitTags } from "@/lib/labels";
import {
  useAcceptAnswer,
  useAnswerQuestion,
  useCloseQuestion,
  useDeleteAnswer,
  useDeleteQuestion,
  useQuestion,
  useUpdateAnswer,
} from "@/lib/queries/questions";
import { useSession } from "@/lib/queries/session";
import type { Answer, Question } from "@/lib/types";

export function QuestionDetailScreen() {
  const { id } = useParams<{ id: string }>();
  const question = useQuestion(Number(id));

  if (question.isPending) return <DetailScreenSkeleton fallback="/questions" />;
  if (question.isError) {
    return (
      <>
        <NavBar left={<BackButton fallback="/questions" />} />
        <ErrorState error={question.error} onRetry={() => question.refetch()} />
      </>
    );
  }
  return <QuestionDetail question={question.data} />;
}

type Composer = { mode: "new" } | { mode: "edit"; answer: Answer } | null;

function QuestionDetail({ question }: { question: Question }) {
  const router = useRouter();
  const { user } = useSession();
  const { confirm, toast, requireAuth } = useOverlay();
  const [composer, setComposer] = useState<Composer>(null);
  const remove = useDeleteQuestion();
  const close = useCloseQuestion(question.id);
  const category = questionCategory[question.category];
  const tags = splitTags(question.tags);
  const isAuthor = user?.id === question.authorId;
  const isAdmin = user?.role === "ADMIN";
  const closed = question.status === "CLOSED";

  const fail = (fallback: string) => (e: unknown) => toast(isApiError(e) ? e.userMessage : fallback, "error");

  const actions: SheetAction[] = [];
  if (isAuthor) {
    actions.push({ label: "수정하기", icon: Pencil, onSelect: () => router.push(`/questions/${question.id}/edit`) });
  }
  if ((isAuthor || isAdmin) && !closed) {
    actions.push({
      label: "답변 그만 받기",
      icon: CircleSlash,
      onSelect: async () => {
        const ok = await confirm({
          title: "질문을 마감할까요?",
          description: "마감하면 더 이상 답변을 받을 수 없어요.",
          confirmLabel: "마감하기",
        });
        if (ok) close.mutate(undefined, { onSuccess: () => toast("질문을 마감했어요"), onError: fail("마감하지 못했어요") });
      },
    });
  }
  if (isAdmin || (isAuthor && question.answerCount === 0)) {
    actions.push({
      label: "삭제하기",
      icon: Trash,
      danger: true,
      onSelect: async () => {
        const ok = await confirm({ title: "질문을 삭제할까요?", confirmLabel: "삭제하기", danger: true });
        if (!ok) return;
        remove.mutate(question.id, {
          onSuccess: () => {
            toast("질문을 삭제했어요");
            router.replace("/questions");
          },
          onError: fail("삭제하지 못했어요"),
        });
      },
    });
  }

  return (
    <>
      <NavBar left={<BackButton fallback="/questions" />} title={question.title} right={<MoreMenu actions={actions} />} />
      <article className="mx-3 rounded-section bg-surface px-5 pt-6 pb-6">
        <div className="flex flex-wrap items-center gap-1.5">
          <Badge tint={category.tint}>{category.label}</Badge>
          <Badge tone={question.status === "OPEN" ? "blue" : question.status === "ANSWERED" ? "green" : "grey"}>
            {questionStatus[question.status]}
          </Badge>
          {question.priority === "URGENT" && <Badge tone="red">긴급</Badge>}
        </div>
        <h1 className="mt-3 text-t2 text-ink">
          <span className="mr-1.5 text-primary-ink">Q.</span>
          {question.title}
        </h1>
        <div className="mt-4 flex items-center gap-3">
          <Avatar name={question.authorName} size={40} />
          <div>
            <p className="text-t6 font-semibold text-ink">{question.authorName}</p>
            <p className="tabular flex gap-2 text-t7 text-ink-4">
              <span><RelativeTime iso={question.createdAt} /></span>
              <span>조회 {question.viewCount}</span>
            </p>
          </div>
        </div>
        <div className="mt-6 text-body whitespace-pre-wrap text-ink-2">{question.content}</div>
        {tags.length > 0 && (
          <ul aria-label="태그" className="mt-6 flex flex-wrap gap-1.5">
            {tags.map((tag) => (
              <li key={tag} className="rounded-[8px] bg-fill px-2.5 py-1 text-t7 font-medium text-ink-3">
                #{tag}
              </li>
            ))}
          </ul>
        )}
      </article>

      <section aria-labelledby="answers-title" className="mt-6">
        <h2 id="answers-title" className="px-5 text-t3 font-bold text-ink">
          답변 <span className="tabular text-primary-ink">{question.answers.length}</span>
        </h2>
        {question.answers.length === 0 ? (
          <div className="mx-3 mt-3 rounded-section bg-surface">
            <EmptyState
              emoji="hourglass"
              title="아직 답변이 없어요"
              description={closed ? "마감된 질문이에요" : "경험이 있다면 먼저 답해주세요"}
            />
          </div>
        ) : (
          <ul className="mt-3 space-y-3">
            {question.answers.map((answer) => (
              <li key={answer.id}>
                <AnswerCard question={question} answer={answer} onEdit={() => setComposer({ mode: "edit", answer })} />
              </li>
            ))}
          </ul>
        )}
      </section>
      <div aria-hidden className="h-16" />

      <BottomCTA>
        {closed ? (
          <Button block variant="surface" disabled>
            마감된 질문이에요
          </Button>
        ) : (
          <Button block onClick={() => requireAuth("답변하려면") && setComposer({ mode: "new" })}>
            답변 남기기
          </Button>
        )}
      </BottomCTA>

      <AnswerSheet question={question} composer={composer} onClose={() => setComposer(null)} />
    </>
  );
}

function AnswerCard({ question, answer, onEdit }: { question: Question; answer: Answer; onEdit: () => void }) {
  const { user } = useSession();
  const { confirm, toast } = useOverlay();
  const accept = useAcceptAnswer(question.id);
  const remove = useDeleteAnswer(question.id);
  const badge = answerType[answer.type];
  const mine = user?.id === answer.authorId;
  const canAccept =
    user?.id === question.authorId && !mine && question.acceptedAnswerId === null && question.status !== "CLOSED";

  return (
    <article
      className={clsx(
        "mx-3 rounded-section bg-surface px-5 py-5",
        answer.accepted && "shadow-[inset_0_0_0_2px_var(--positive)]",
      )}
    >
      {answer.accepted && (
        <p className="mb-3 flex items-center gap-1.5 text-t6 font-bold text-positive">
          <Emoji name="check" size={20} />
          질문자가 고른 답변
        </p>
      )}
      <div className="flex items-center gap-3">
        <Avatar name={answer.authorName} size={36} />
        <div className="min-w-0 flex-1">
          <p className="flex items-center gap-1.5">
            <span className="text-t6 font-semibold text-ink">{answer.authorName}</span>
            {badge && (
              <Badge
                tint={answer.type === "EXPERT" ? HUE.purple : HUE.sky}
                icon={<Emoji name={badge.emoji} size={14} />}
              >
                {badge.label}
              </Badge>
            )}
          </p>
          <p className="text-t7 text-ink-4"><RelativeTime iso={answer.createdAt} /></p>
        </div>
      </div>
      <p className="mt-4 text-body whitespace-pre-wrap text-ink-2">{answer.content}</p>

      {(canAccept || mine || user?.role === "ADMIN") && (
        <div className="mt-4 flex items-center gap-2">
          {canAccept && (
            <Button
              size="sm"
              variant="weak"
              icon={<Check className="size-4" strokeWidth={2.6} aria-hidden />}
              loading={accept.isPending}
              onClick={async () => {
                const ok = await confirm({
                  title: "이 답변을 채택할까요?",
                  description: "채택은 한 번만 할 수 있고, 바꿀 수 없어요.",
                  confirmLabel: "채택하기",
                });
                if (ok) {
                  accept.mutate(answer.id, {
                    onSuccess: () => toast("답변을 채택했어요"),
                    onError: (e) => toast(isApiError(e) ? e.userMessage : "채택하지 못했어요", "error"),
                  });
                }
              }}
            >
              채택하기
            </Button>
          )}
          <span className="flex-1" />
          {mine && (
            <Button size="sm" variant="ghost" onClick={onEdit}>
              수정
            </Button>
          )}
          {(mine || user?.role === "ADMIN") && !answer.accepted && (
            <Button
              size="sm"
              variant="ghost"
              onClick={async () => {
                const ok = await confirm({ title: "답변을 삭제할까요?", confirmLabel: "삭제하기", danger: true });
                if (ok) {
                  remove.mutate(answer.id, {
                    onSuccess: () => toast("답변을 삭제했어요"),
                    onError: (e) => toast(isApiError(e) ? e.userMessage : "삭제하지 못했어요", "error"),
                  });
                }
              }}
            >
              삭제
            </Button>
          )}
        </div>
      )}
    </article>
  );
}

function AnswerSheet({ question, composer, onClose }: { question: Question; composer: Composer; onClose: () => void }) {
  const { user } = useSession();
  const { toast } = useOverlay();
  const create = useAnswerQuestion(question.id);
  const update = useUpdateAnswer(question.id);
  const [text, setText] = useState("");
  const [seededFor, setSeededFor] = useState<Composer>(null);

  // 시트를 열 때마다 입력값을 새로 채운다
  if (composer !== seededFor) {
    setSeededFor(composer);
    setText(composer?.mode === "edit" ? composer.answer.content : "");
  }

  const editing = composer?.mode === "edit";
  const pending = create.isPending || update.isPending;
  const submit = () => {
    const content = text.trim();
    if (!content || !composer) return;
    const done = {
      onSuccess: () => {
        toast(editing ? "답변을 고쳤어요" : "답변을 남겼어요");
        onClose();
      },
      onError: (e: unknown) => toast(isApiError(e) ? e.userMessage : "답변을 남기지 못했어요", "error"),
    };
    if (composer.mode === "edit") update.mutate({ answerId: composer.answer.id, content }, done);
    else create.mutate(content, done);
  };

  const roleHint =
    user?.role === "EXPERT" || user?.role === "TEACHER"
      ? `${roleLabel[user.role]} 답변으로 표시돼요`
      : "내 경험을 솔직하게 나눠주세요";

  return (
    <BottomSheet
      open={composer !== null}
      onClose={onClose}
      title={editing ? "답변 고치기" : "답변 남기기"}
      description={editing ? undefined : roleHint}
    >
      <TextArea
        value={text}
        onChange={(e) => setText(e.target.value)}
        maxLength={5000}
        placeholder={`Q. ${question.title}`}
        aria-label="답변"
        className="min-h-44"
        data-autofocus
        hint={<span className="tabular">{text.length}/5000</span>}
      />
      <Button block className="mt-4" onClick={submit} disabled={!text.trim()} loading={pending}>
        {editing ? "고친 답변 저장하기" : "답변 올리기"}
      </Button>
    </BottomSheet>
  );
}
