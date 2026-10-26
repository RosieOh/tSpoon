"use client";

import clsx from "clsx";
import { ArrowUp, CornerDownRight, X } from "lucide-react";
import { createContext, useContext, useRef, useState, type ReactNode } from "react";
import { LiquidGlass } from "@/components/glass/LiquidGlass";
import { useOverlay } from "@/components/overlays/Overlays";
import { RelativeTime } from "@/components/ui/RelativeTime";
import { Avatar } from "@/components/ui/Avatar";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { EmptyState, ErrorState, SkeletonRows } from "@/components/ui/States";
import { isApiError } from "@/lib/api";
import { useComments, useCreateComment, useDeleteComment, useUpdateComment } from "@/lib/queries/boards";
import { useSession } from "@/lib/queries/session";
import type { Comment } from "@/lib/types";

type ReplyTarget = { id: number; name: string };

const ComposerContext = createContext<{
  boardId: number;
  replyTo: ReplyTarget | null;
  setReplyTo: (target: ReplyTarget | null) => void;
  inputRef: React.RefObject<HTMLTextAreaElement | null>;
} | null>(null);

function useComposer() {
  const ctx = useContext(ComposerContext);
  if (!ctx) throw new Error("CommentComposerProvider 가 필요합니다");
  return ctx;
}

export function CommentComposerProvider({ boardId, children }: { boardId: number; children: ReactNode }) {
  const [replyTo, setReplyTo] = useState<ReplyTarget | null>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  return (
    <ComposerContext.Provider value={{ boardId, replyTo, setReplyTo, inputRef }}>
      {children}
      {/* 입력창이 마지막 댓글을 가리지 않게 */}
      <div aria-hidden className="h-24" />
    </ComposerContext.Provider>
  );
}

export function CommentList({ boardId, boardAuthorId }: { boardId: number; boardAuthorId: number }) {
  const comments = useComments(boardId);
  if (comments.isPending) return <SkeletonRows count={2} />;
  if (comments.isError) return <ErrorState error={comments.error} onRetry={() => comments.refetch()} />;
  if (comments.data.length === 0) {
    return <EmptyState emoji="speech" title="아직 댓글이 없어요" description="첫 댓글을 남겨보세요" className="py-10" />;
  }
  return (
    <ul className="mt-1">
      {comments.data.map((comment) => (
        <li key={comment.id}>
          <CommentItem comment={comment} boardId={boardId} boardAuthorId={boardAuthorId} />
          {comment.replies.length > 0 && (
            <ul>
              {comment.replies.map((reply) => (
                <li key={reply.id}>
                  <CommentItem comment={reply} boardId={boardId} boardAuthorId={boardAuthorId} reply />
                </li>
              ))}
            </ul>
          )}
        </li>
      ))}
    </ul>
  );
}

function CommentItem({
  comment,
  boardId,
  boardAuthorId,
  reply = false,
}: {
  comment: Comment;
  boardId: number;
  boardAuthorId: number;
  reply?: boolean;
}) {
  const { user } = useSession();
  const { confirm, toast, requireAuth } = useOverlay();
  const { setReplyTo, inputRef } = useComposer();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(comment.content);
  const update = useUpdateComment(boardId);
  const remove = useDeleteComment(boardId);
  const mine = user !== null && user.id === comment.authorId;

  if (comment.deleted) {
    return (
      <div className={clsx("flex items-center gap-2 px-5 py-4 text-t6 text-ink-4", reply && "pl-14")}>
        {reply && <CornerDownRight className="size-4" aria-hidden />}
        {comment.hidden ? "운영 정책에 따라 숨겨진 댓글이에요" : "삭제된 댓글이에요"}
      </div>
    );
  }

  const save = () => {
    const content = draft.trim();
    if (!content) return;
    update.mutate(
      { id: comment.id, content },
      {
        onSuccess: () => setEditing(false),
        onError: (e) => toast(isApiError(e) ? e.userMessage : "수정하지 못했어요", "error"),
      },
    );
  };

  const onDelete = async () => {
    const ok = await confirm({ title: "댓글을 삭제할까요?", confirmLabel: "삭제하기", danger: true });
    if (!ok) return;
    remove.mutate(comment.id, {
      onSuccess: () => toast("댓글을 삭제했어요"),
      onError: (e) => toast(isApiError(e) ? e.userMessage : "삭제하지 못했어요", "error"),
    });
  };

  return (
    <div className={clsx("flex gap-3 px-5 py-4", reply && "bg-[color-mix(in_oklab,var(--fill)_45%,transparent)] pl-14")}>
      <Avatar name={comment.authorName} size={reply ? 28 : 34} />
      <div className="min-w-0 flex-1">
        <p className="flex items-center gap-1.5">
          <span className="text-t6 font-semibold text-ink">{comment.authorName}</span>
          {comment.authorId === boardAuthorId && (
            <Badge tone="blue" className="h-5 px-1.5">
              작성자
            </Badge>
          )}
          <span className="text-t7 text-ink-4"><RelativeTime iso={comment.createdAt} /></span>
        </p>
        {editing ? (
          <div className="mt-2">
            <textarea
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              maxLength={2000}
              aria-label="댓글 수정"
              className="w-full resize-none rounded-[12px] bg-fill px-3 py-2.5 text-t5 text-ink outline-none [field-sizing:content] focus:shadow-[inset_0_0_0_2px_var(--primary)]"
              autoFocus
            />
            <div className="mt-2 flex justify-end gap-1.5">
              <Button size="sm" variant="neutral" onClick={() => setEditing(false)}>
                취소
              </Button>
              <Button size="sm" onClick={save} loading={update.isPending} disabled={!draft.trim()}>
                저장
              </Button>
            </div>
          </div>
        ) : (
          <p className="mt-1 text-t5 whitespace-pre-wrap text-ink-2">{comment.content}</p>
        )}
        {!editing && (
          <div className="mt-2 flex gap-3.5 text-t7 font-medium text-ink-4">
            {!reply && (
              <button
                type="button"
                className="press"
                onClick={() => {
                  if (!requireAuth("답글을 달려면")) return;
                  setReplyTo({ id: comment.id, name: comment.authorName ?? "" });
                  inputRef.current?.focus();
                }}
              >
                답글 달기
              </button>
            )}
            {mine && (
              <>
                <button
                  type="button"
                  className="press"
                  onClick={() => {
                    setDraft(comment.content);
                    setEditing(true);
                  }}
                >
                  수정
                </button>
                <button type="button" className="press" onClick={onDelete}>
                  삭제
                </button>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

/** 화면 아래 떠 있는 유리 입력창 */
export function CommentComposer() {
  const { boardId, replyTo, setReplyTo, inputRef } = useComposer();
  const { user } = useSession();
  const { requireAuth, toast } = useOverlay();
  const [text, setText] = useState("");
  const create = useCreateComment(boardId);

  const submit = () => {
    const content = text.trim();
    if (!content || create.isPending) return;
    create.mutate(
      { content, parentCommentId: replyTo?.id ?? null },
      {
        onSuccess: () => {
          setText("");
          setReplyTo(null);
        },
        onError: (e) => toast(isApiError(e) ? e.userMessage : "댓글을 남기지 못했어요", "error"),
      },
    );
  };

  return (
    <div className="column-fixed bottom-0 z-30 px-3 pb-[max(10px,env(safe-area-inset-bottom))]">
      <LiquidGlass radius={26} bezel={18} strength={44} blur={18} thick className="p-1.5">
        {replyTo && (
          <div className="relative z-[2] flex items-center justify-between px-3 pt-1.5 pb-0.5 text-t7 text-ink-3">
            <span>
              <b className="font-semibold text-ink-2">{replyTo.name}</b>님에게 답글을 남겨요
            </span>
            <button type="button" aria-label="답글 취소" className="press p-1" onClick={() => setReplyTo(null)}>
              <X className="size-4" aria-hidden />
            </button>
          </div>
        )}
        <form
          className="relative z-[2] flex items-end gap-1.5"
          onSubmit={(e) => {
            e.preventDefault();
            submit();
          }}
        >
          <textarea
            ref={inputRef}
            value={text}
            onChange={(e) => setText(e.target.value)}
            onFocus={(e) => {
              if (!user && !requireAuth("댓글을 남기려면")) e.currentTarget.blur();
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) submit();
            }}
            rows={1}
            maxLength={2000}
            placeholder={user ? "따뜻한 댓글을 남겨주세요" : "로그인하고 댓글을 남겨보세요"}
            aria-label="댓글"
            className="max-h-32 min-h-11 flex-1 resize-none bg-transparent px-3 py-2.5 text-t5 text-ink outline-none [field-sizing:content] focus-visible:outline-none"
          />
          <button
            type="submit"
            aria-label="댓글 보내기"
            disabled={!text.trim() || create.isPending}
            className="press grid size-11 shrink-0 place-items-center rounded-full bg-primary text-white disabled:bg-fill-strong disabled:text-ink-4"
          >
            <ArrowUp className="size-5" strokeWidth={2.6} aria-hidden />
          </button>
        </form>
      </LiquidGlass>
    </div>
  );
}
