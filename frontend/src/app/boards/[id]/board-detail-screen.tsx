"use client";

import clsx from "clsx";
import { Heart, Pencil, Trash } from "lucide-react";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { useOverlay } from "@/components/overlays/Overlays";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { RelativeTime } from "@/components/ui/RelativeTime";
import { MoreMenu, type SheetAction } from "@/components/ui/ActionSheet";
import { Avatar } from "@/components/ui/Avatar";
import { Badge } from "@/components/ui/Badge";
import { ErrorState } from "@/components/ui/States";
import { DetailScreenSkeleton } from "@/components/shell/Screen";
import { isApiError } from "@/lib/api";
import { boardCategory, splitTags } from "@/lib/labels";
import { useBoard, useBoardLike, useDeleteBoard } from "@/lib/queries/boards";
import { useSession } from "@/lib/queries/session";
import type { Board } from "@/lib/types";
import { CommentComposerProvider, CommentComposer, CommentList } from "./comments";

export function BoardDetailScreen() {
  const { id } = useParams<{ id: string }>();
  const boardId = Number(id);
  const board = useBoard(boardId);

  if (board.isPending) return <DetailScreenSkeleton fallback="/boards" />;
  if (board.isError) {
    return (
      <>
        <NavBar left={<BackButton fallback="/boards" />} />
        <ErrorState error={board.error} onRetry={() => board.refetch()} />
      </>
    );
  }
  return <BoardDetail board={board.data} />;
}

function BoardDetail({ board }: { board: Board }) {
  const router = useRouter();
  const { user } = useSession();
  const { confirm, toast } = useOverlay();
  const remove = useDeleteBoard();
  const category = boardCategory[board.category];
  const tags = splitTags(board.tags);
  const isAuthor = user?.id === board.authorId;

  const actions: SheetAction[] = [];
  if (isAuthor) actions.push({ label: "수정하기", icon: Pencil, onSelect: () => router.push(`/boards/${board.id}/edit`) });
  if (isAuthor || user?.role === "ADMIN") {
    actions.push({
      label: "삭제하기",
      icon: Trash,
      danger: true,
      onSelect: async () => {
        const ok = await confirm({
          title: "글을 삭제할까요?",
          description: "삭제한 글은 되돌릴 수 없어요.",
          confirmLabel: "삭제하기",
          danger: true,
        });
        if (!ok) return;
        remove.mutate(board.id, {
          onSuccess: () => {
            toast("글을 삭제했어요");
            router.replace("/boards");
          },
          onError: (e) => toast(isApiError(e) ? e.userMessage : "삭제하지 못했어요", "error"),
        });
      },
    });
  }

  return (
    <CommentComposerProvider boardId={board.id}>
      <NavBar left={<BackButton fallback="/boards" />} title={board.title} right={<MoreMenu actions={actions} />} />
      <article className="mx-3 rounded-section bg-surface px-5 pt-6 pb-6">
        <Badge tint={category.tint}>{category.label}</Badge>
        <h1 className="mt-3 text-t2 text-ink">{board.title}</h1>
        <div className="mt-4 flex items-center gap-3">
          <Avatar name={board.authorName} size={40} />
          <div>
            <p className="text-t6 font-semibold text-ink">{board.authorName}</p>
            <p className="tabular flex gap-2 text-t7 text-ink-4">
              <span><RelativeTime iso={board.createdAt} /></span>
              <span>조회 {board.viewCount}</span>
            </p>
          </div>
        </div>
        <div className="mt-6 text-body whitespace-pre-wrap text-ink-2">{board.content}</div>
        {tags.length > 0 && (
          <ul aria-label="태그" className="mt-6 flex flex-wrap gap-1.5">
            {tags.map((tag) => (
              <li key={tag} className="rounded-[8px] bg-fill px-2.5 py-1 text-t7 font-medium text-ink-3">
                #{tag}
              </li>
            ))}
          </ul>
        )}
        <div className="mt-8 flex justify-center">
          <LikeButton board={board} />
        </div>
      </article>

      <section aria-labelledby="comments-title" className="mx-3 mt-3 rounded-section bg-surface pt-5 pb-2">
        <h2 id="comments-title" className="px-5 text-t4 font-bold text-ink">
          댓글 <span className="tabular text-primary-ink">{board.commentCount}</span>
        </h2>
        <CommentList boardId={board.id} boardAuthorId={board.authorId} />
      </section>
      <CommentComposer />
    </CommentComposerProvider>
  );
}

function LikeButton({ board }: { board: Board }) {
  const { requireAuth } = useOverlay();
  const like = useBoardLike(board);
  // 누를 때마다 key 를 바꿔 CSS 팝 애니메이션을 다시 건다
  const [pop, setPop] = useState(0);

  return (
    <button
      type="button"
      aria-pressed={like.isLiked}
      onClick={() => {
        if (!requireAuth("좋아요를 누르려면")) return;
        if (!like.isLiked) setPop((n) => n + 1);
        like.toggle();
      }}
      className={clsx(
        "press flex h-12 items-center gap-2 rounded-full border px-5 text-t5 font-semibold",
        like.isLiked ? "border-transparent bg-danger-weak text-danger-ink" : "border-line text-ink-2",
      )}
    >
      <span key={pop} className={clsx("grid place-items-center", pop > 0 && "animate-like-pop")}>
        <Heart className={clsx("size-5", like.isLiked && "fill-current")} aria-hidden />
      </span>
      좋아요
      <span className="tabular">{board.likeCount}</span>
    </button>
  );
}
