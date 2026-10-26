"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useOverlay } from "@/components/overlays/Overlays";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { BottomCTA } from "@/components/shell/Screen";
import { Button } from "@/components/ui/Button";
import { Chips } from "@/components/ui/Chips";
import { BareTextArea, BareTitleInput, TextField } from "@/components/ui/Field";
import { isApiError } from "@/lib/api";
import { boardCategory, keysOf } from "@/lib/labels";
import { useSaveBoard } from "@/lib/queries/boards";
import { useSession } from "@/lib/queries/session";
import type { Board, BoardCategory } from "@/lib/types";

export function BoardForm({ board }: { board?: Board }) {
  const router = useRouter();
  const { user } = useSession();
  const { toast } = useOverlay();
  const save = useSaveBoard(board?.id);
  const [category, setCategory] = useState<BoardCategory | null>(board?.category ?? null);
  const [title, setTitle] = useState(board?.title ?? "");
  const [content, setContent] = useState(board?.content ?? "");
  const [tags, setTags] = useState(board?.tags ?? "");
  const [errors, setErrors] = useState<Record<string, string>>({});

  // 공지는 관리자만
  const categories = keysOf(boardCategory).filter((c) => c !== "ANNOUNCEMENT" || user?.role === "ADMIN");
  const valid = category !== null && title.trim().length > 0 && content.trim().length > 0;

  const submit = () => {
    if (!valid || !category) return;
    setErrors({});
    save.mutate(
      { category, title: title.trim(), content: content.trim(), tags: tags.trim() || null },
      {
        onSuccess: (saved) => {
          toast(board ? "글을 고쳤어요" : "글을 올렸어요");
          router.replace(`/boards/${saved.id}`);
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
        left={<BackButton close fallback={board ? `/boards/${board.id}` : "/boards"} />}
        title={board ? "글 고치기" : "글쓰기"}
        alwaysShowTitle
      />
      <div className="pt-2 pb-8">
        <p className="mb-2 px-5 text-t6 font-semibold text-ink-2">어떤 이야기인가요?</p>
        <Chips
          label="게시판"
          options={categories.map((c) => ({ value: c, label: boardCategory[c].label }))}
          value={category}
          onChange={setCategory}
        />
        <div className="mx-3 mt-4 rounded-section bg-surface p-5">
          <BareTitleInput
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="제목을 입력해주세요"
            maxLength={255}
            aria-label="제목"
            aria-invalid={errors.title ? true : undefined}
          />
          {errors.title && <p className="mt-1 text-t7 text-danger-ink">{errors.title}</p>}
          <div className="my-4 h-px bg-line" />
          <BareTextArea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder={"아이 교육과 육아에 관한 이야기를 나눠주세요.\n다른 사람을 존중하는 글이 더 많은 답을 받아요."}
            aria-label="내용"
            aria-invalid={errors.content ? true : undefined}
          />
          {errors.content && <p className="mt-1 text-t7 text-danger-ink">{errors.content}</p>}
        </div>
        <div className="mx-3 mt-3 rounded-section bg-surface p-5">
          <TextField
            label="태그"
            value={tags}
            onChange={(e) => setTags(e.target.value)}
            placeholder="초1, 방과후"
            hint="쉼표로 나눠 적으면 비슷한 글을 찾기 쉬워요"
          />
        </div>
      </div>
      <BottomCTA>
        <Button block onClick={submit} disabled={!valid} loading={save.isPending}>
          {board ? "고친 내용 저장하기" : "글 올리기"}
        </Button>
      </BottomCTA>
    </>
  );
}
