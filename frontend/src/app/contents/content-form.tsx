"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useOverlay } from "@/components/overlays/Overlays";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { BottomCTA } from "@/components/shell/Screen";
import { Button } from "@/components/ui/Button";
import { Chips } from "@/components/ui/Chips";
import { BareTextArea, BareTitleInput, Switch, TextArea, TextField } from "@/components/ui/Field";
import { EmptyState } from "@/components/ui/States";
import { isApiError } from "@/lib/api";
import { contentCategory, contentType, keysOf } from "@/lib/labels";
import { useSaveContent } from "@/lib/queries/contents";
import { canCreateContent, useSession } from "@/lib/queries/session";
import type { Content, ContentCategory, ContentType } from "@/lib/types";

export function ContentForm({ content }: { content?: Content }) {
  const router = useRouter();
  const { user, isLoading } = useSession();
  const { toast } = useOverlay();
  const save = useSaveContent(content?.id);
  const [type, setType] = useState<ContentType | null>(content?.type ?? null);
  const [category, setCategory] = useState<ContentCategory | null>(content?.category ?? null);
  const [title, setTitle] = useState(content?.title ?? "");
  const [description, setDescription] = useState(content?.description ?? "");
  const [body, setBody] = useState(content?.content ?? "");
  const [minutes, setMinutes] = useState(content?.estimatedTime ? String(content.estimatedTime) : "");
  const [targetAge, setTargetAge] = useState(content?.targetAge ?? "");
  const [thumbnailUrl, setThumbnailUrl] = useState(content?.thumbnailUrl ?? "");
  const [tags, setTags] = useState(content?.tags ?? "");
  const [draft, setDraft] = useState(content ? content.status === "DRAFT" : false);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const fallback = content ? `/contents/${content.id}` : "/contents";
  const nav = (
    <NavBar left={<BackButton close fallback={fallback} />} title={content ? "콘텐츠 고치기" : "콘텐츠 만들기"} alwaysShowTitle />
  );

  if (!isLoading && !canCreateContent(user)) {
    return (
      <>
        {nav}
        <EmptyState
          emoji="lock"
          title="선생님과 전문가만 만들 수 있어요"
          description="역할 변경은 관리자에게 문의해 주세요"
        />
      </>
    );
  }

  const valid = type !== null && category !== null && title.trim() !== "" && description.trim() !== "";

  const submit = () => {
    if (!valid || !type || !category) return;
    setErrors({});
    const estimatedTime = minutes ? Number(minutes) : null;
    save.mutate(
      {
        type,
        category,
        title: title.trim(),
        description: description.trim(),
        content: body.trim() || null,
        estimatedTime: estimatedTime && estimatedTime > 0 ? estimatedTime : null,
        targetAge: targetAge.trim() || null,
        thumbnailUrl: thumbnailUrl.trim() || null,
        tags: tags.trim() || null,
        draft,
      },
      {
        onSuccess: (saved) => {
          toast(draft ? "임시저장했어요" : content ? "콘텐츠를 고쳤어요" : "콘텐츠를 공개했어요");
          router.replace(`/contents/${saved.id}`);
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
      {nav}
      <div className="space-y-3 pt-2 pb-8">
        <div>
          <p className="mb-2 px-5 text-t6 font-semibold text-ink-2">어떤 형태인가요?</p>
          <Chips
            label="유형"
            options={keysOf(contentType).map((t) => ({ value: t, label: contentType[t].label }))}
            value={type}
            onChange={setType}
          />
        </div>
        <div>
          <p className="mt-3 mb-2 px-5 text-t6 font-semibold text-ink-2">어느 분야인가요?</p>
          <Chips
            label="분야"
            options={keysOf(contentCategory).map((c) => ({ value: c, label: contentCategory[c].label }))}
            value={category}
            onChange={setCategory}
          />
        </div>

        <div className="mx-3 rounded-section bg-surface p-5">
          <BareTitleInput
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="제목을 입력해주세요"
            maxLength={255}
            aria-label="제목"
          />
          {errors.title && <p className="mt-1 text-t7 text-danger-ink">{errors.title}</p>}
          <TextArea
            wrapperClassName="mt-4"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="목록에 보이는 짧은 소개예요. 무엇을 얻을 수 있는지 적어주세요"
            aria-label="소개"
            className="min-h-24"
            error={errors.description}
          />
          <div className="my-5 h-px bg-line" />
          <BareTextArea value={body} onChange={(e) => setBody(e.target.value)} placeholder="본문을 적어주세요" aria-label="본문" />
        </div>

        <div className="mx-3 space-y-4 rounded-section bg-surface p-5">
          <div className="grid grid-cols-2 gap-3">
            <TextField
              label="학습 시간(분)"
              inputMode="numeric"
              value={minutes}
              onChange={(e) => setMinutes(e.target.value.replace(/\D/g, ""))}
              placeholder="10"
              error={errors.estimatedTime}
            />
            <TextField label="추천 나이" value={targetAge} onChange={(e) => setTargetAge(e.target.value)} placeholder="7~8세" />
          </div>
          <TextField
            label="썸네일 주소"
            type="url"
            value={thumbnailUrl}
            onChange={(e) => setThumbnailUrl(e.target.value)}
            placeholder="https://"
            hint="비워두면 분야에 맞는 그림을 보여줘요"
          />
          <TextField label="태그" value={tags} onChange={(e) => setTags(e.target.value)} placeholder="한글, 초등 입학" />
          <div className="border-t border-line pt-3">
            <Switch
              checked={draft}
              onChange={setDraft}
              label="임시저장으로 두기"
              description="나와 관리자만 볼 수 있어요. 나중에 공개할 수 있어요"
            />
          </div>
        </div>
      </div>
      <BottomCTA>
        <Button block onClick={submit} disabled={!valid} loading={save.isPending}>
          {draft ? "임시저장하기" : content?.status === "ACTIVE" ? "고친 내용 저장하기" : "공개하기"}
        </Button>
      </BottomCTA>
    </>
  );
}
