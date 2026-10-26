import { Heart, MessageCircle } from "lucide-react";
import Link from "next/link";
import { RelativeTime } from "@/components/ui/RelativeTime";
import { Badge } from "@/components/ui/Badge";
import { boardCategory } from "@/lib/labels";
import type { Board } from "@/lib/types";

export function BoardRow({ board, showCategory = true }: { board: Board; showCategory?: boolean }) {
  const category = boardCategory[board.category];
  return (
    <Link href={`/boards/${board.id}`} className="press-row block px-5 py-4">
      {showCategory && <Badge tint={category.tint}>{category.label}</Badge>}
      <p className="mt-2 line-clamp-1 text-t4 font-semibold text-ink">{board.title}</p>
      <p className="mt-0.5 line-clamp-2 text-t6 text-ink-3">{board.content}</p>
      <div className="mt-2.5 flex items-center gap-2 text-t7 text-ink-4">
        <span className="font-medium text-ink-3">{board.authorName}</span>
        <span><RelativeTime iso={board.createdAt} /></span>
        <span className="tabular ml-auto flex items-center gap-3">
          <span className="flex items-center gap-1" aria-label={`좋아요 ${board.likeCount}개`}>
            <Heart className="size-3.5" aria-hidden />
            {board.likeCount}
          </span>
          <span className="flex items-center gap-1" aria-label={`댓글 ${board.commentCount}개`}>
            <MessageCircle className="size-3.5" aria-hidden />
            {board.commentCount}
          </span>
        </span>
      </div>
    </Link>
  );
}
