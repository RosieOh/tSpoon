"use client";

import { Bookmark, BookmarkCheck, Pencil, Trash } from "lucide-react";
import { useParams, useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { ContentArt } from "@/components/domain/ContentArt";
import { useOverlay } from "@/components/overlays/Overlays";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { BottomCTA, DetailScreenSkeleton } from "@/components/shell/Screen";
import { RelativeTime } from "@/components/ui/RelativeTime";
import { MoreMenu, type SheetAction } from "@/components/ui/ActionSheet";
import { Avatar } from "@/components/ui/Avatar";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/States";
import { isApiError } from "@/lib/api";
import { contentCategory, contentType, splitTags } from "@/lib/labels";
import { useBookmark, useContent, useDeleteContent, useSaveContent } from "@/lib/queries/contents";
import { useSession } from "@/lib/queries/session";
import type { Content } from "@/lib/types";

export function ContentDetailScreen() {
  const { id } = useParams<{ id: string }>();
  const content = useContent(Number(id));

  if (content.isPending) return <DetailScreenSkeleton fallback="/contents" />;
  if (content.isError) {
    return (
      <>
        <NavBar left={<BackButton fallback="/contents" />} />
        <ErrorState error={content.error} onRetry={() => content.refetch()} />
      </>
    );
  }
  return <ContentDetail content={content.data} />;
}

function ContentDetail({ content }: { content: Content }) {
  const router = useRouter();
  const { user } = useSession();
  const { confirm, toast } = useOverlay();
  const remove = useDeleteContent();
  const type = contentType[content.type];
  const category = contentCategory[content.category];
  const tags = splitTags(content.tags);
  const canManage = user !== null && (user.id === content.authorId || user.role === "ADMIN");
  const isDraft = content.status === "DRAFT";

  const actions: SheetAction[] = canManage
    ? [
        { label: "수정하기", icon: Pencil, onSelect: () => router.push(`/contents/${content.id}/edit`) },
        {
          label: "삭제하기",
          icon: Trash,
          danger: true,
          onSelect: async () => {
            const ok = await confirm({
              title: "콘텐츠를 삭제할까요?",
              description: "담아둔 사람들의 목록에서도 사라져요.",
              confirmLabel: "삭제하기",
              danger: true,
            });
            if (!ok) return;
            remove.mutate(content.id, {
              onSuccess: () => {
                toast("콘텐츠를 삭제했어요");
                router.replace("/contents");
              },
              onError: (e) => toast(isApiError(e) ? e.userMessage : "삭제하지 못했어요", "error"),
            });
          },
        },
      ]
    : [];

  return (
    <>
      <NavBar overlay left={<BackButton fallback="/contents" />} title={content.title} right={<MoreMenu actions={actions} />} />
      <div className="relative h-[300px] overflow-hidden lg:mx-3 lg:mt-3 lg:rounded-t-[28px]">
        <ContentArt content={content} variant="banner" eager />
      </div>
      <article className="relative -mt-7 rounded-t-[28px] bg-surface px-5 pt-7 pb-10 lg:mx-3 lg:rounded-b-section">
        <div className="flex flex-wrap items-center gap-1.5">
          <Badge tint={type.tint}>{type.label}</Badge>
          <Badge tint={category.tint}>{category.label}</Badge>
          {isDraft && <Badge tone="orange">임시저장</Badge>}
        </div>
        <h1 className="mt-3 text-t1 text-ink">{content.title}</h1>
        <p className="mt-3 text-t4 text-ink-3">{content.description}</p>

        <dl className="mt-6 grid grid-cols-3 rounded-[18px] bg-fill py-4 text-center">
          <Fact label="학습 시간">{content.estimatedTime ? `${content.estimatedTime}분` : "자유롭게"}</Fact>
          <Fact label="추천 나이">{content.targetAge ?? "누구나"}</Fact>
          <Fact label="담은 사람">{content.bookmarkCount}명</Fact>
        </dl>

        <div className="mt-6 flex items-center gap-3">
          <Avatar name={content.authorName} size={40} />
          <div>
            <p className="text-t6 font-semibold text-ink">{content.authorName}</p>
            <p className="tabular flex gap-2 text-t7 text-ink-4">
              <span><RelativeTime iso={content.createdAt} /></span>
              <span>조회 {content.viewCount}</span>
            </p>
          </div>
        </div>

        {content.content && <div className="mt-6 text-body whitespace-pre-wrap text-ink-2">{content.content}</div>}

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
      <div aria-hidden className="h-16" />

      <BottomCTA>{isDraft && canManage ? <PublishButton content={content} /> : <BookmarkButton contentId={content.id} />}</BottomCTA>
    </>
  );
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="border-line px-2 [&:not(:first-child)]:border-l">
      <dt className="text-t8 text-ink-4">{label}</dt>
      <dd className="tabular mt-1 truncate text-t5 font-bold text-ink">{children}</dd>
    </div>
  );
}

function BookmarkButton({ contentId }: { contentId: number }) {
  const { requireAuth, toast } = useOverlay();
  const bookmark = useBookmark(contentId);
  const onClick = () => {
    if (!requireAuth("콘텐츠를 담아두려면")) return;
    toast(bookmark.isBookmarked ? "담아둔 배움에서 뺐어요" : "담아둔 배움에 넣었어요");
    bookmark.toggle();
  };
  return bookmark.isBookmarked ? (
    <Button block variant="weak" icon={<BookmarkCheck className="size-5" aria-hidden />} onClick={onClick}>
      담아둔 배움에서 빼기
    </Button>
  ) : (
    <Button block icon={<Bookmark className="size-5" aria-hidden />} onClick={onClick}>
      배움 담아두기
    </Button>
  );
}

function PublishButton({ content }: { content: Content }) {
  const { toast } = useOverlay();
  const save = useSaveContent(content.id);
  return (
    <Button
      block
      loading={save.isPending}
      onClick={() =>
        save.mutate(
          {
            title: content.title,
            description: content.description,
            content: content.content,
            type: content.type,
            category: content.category,
            tags: content.tags,
            thumbnailUrl: content.thumbnailUrl,
            estimatedTime: content.estimatedTime,
            targetAge: content.targetAge,
            draft: false,
          },
          {
            onSuccess: () => toast("콘텐츠를 공개했어요"),
            onError: (e) => toast(isApiError(e) ? e.userMessage : "공개하지 못했어요", "error"),
          },
        )
      }
    >
      지금 공개하기
    </Button>
  );
}
