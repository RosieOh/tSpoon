"use client";

import { Plus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useOverlay } from "@/components/overlays/Overlays";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { Avatar } from "@/components/ui/Avatar";
import { Badge } from "@/components/ui/Badge";
import { Emoji, IconTile } from "@/components/ui/Emoji";
import { ListRow } from "@/components/ui/ListRow";
import { Section } from "@/components/ui/Section";
import { SkeletonBlock } from "@/components/ui/States";
import { ageOf } from "@/lib/format";
import { gradeLabel, HUE, roleLabel } from "@/lib/labels";
import { useUnreadCount } from "@/lib/queries/notifications";
import { useChildren, useLogout, useSession } from "@/lib/queries/session";

export default function MePage() {
  const router = useRouter();
  const { user, isLoading } = useSession();
  const children = useChildren(user !== null);
  const unread = useUnreadCount().data ?? 0;
  const logout = useLogout();
  const { confirm, toast } = useOverlay();

  return (
    <>
      <NavBar left={<BackButton fallback="/" />} title="내 정보" />
      <div className="flex items-center gap-4 px-5 pt-2 pb-7">
        {isLoading || !user ? (
          <>
            <SkeletonBlock className="size-16 rounded-full" />
            <div className="space-y-2">
              <SkeletonBlock className="h-7 w-28" />
              <SkeletonBlock className="h-4 w-40" />
            </div>
          </>
        ) : (
          <>
            <Avatar name={user.name} size={64} />
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <h1 className="truncate text-t1 text-ink">{user.name}</h1>
                <Badge tone="blue">{roleLabel[user.role]}</Badge>
              </div>
              <p className="truncate text-t6 text-ink-3">{user.email}</p>
            </div>
          </>
        )}
      </div>

      <div className="space-y-3">
        <Section title="우리 아이">
          {children.data?.map((child) => {
            const age = ageOf(child.birthDate);
            return (
              <ListRow
                key={child.id}
                href={`/me/children/${child.id}`}
                chevron
                left={<IconTile emoji="chick" tint={HUE.yellow} size={44} />}
                title={child.name}
                description={[gradeLabel[child.grade], child.school, age !== null ? `만 ${age}세` : null]
                  .filter(Boolean)
                  .join(", ")}
              />
            );
          })}
          <ListRow
            href="/me/children/new"
            left={
              <span className="grid size-11 place-items-center rounded-[14px] bg-primary-weak text-primary-ink">
                <Plus className="size-5" strokeWidth={2.4} aria-hidden />
              </span>
            }
            title={children.data?.length ? "아이 더 등록하기" : "아이 등록하기"}
            titleClassName="text-primary-ink"
            description={children.data?.length ? undefined : "학년과 관심사를 적어두면 기록하기 좋아요"}
          />
        </Section>

        <Section>
          <div className="pt-2">
            <ListRow left={<Emoji name="bookmark" size={28} />} title="담아둔 배움" href="/me/bookmarks" chevron />
            <ListRow left={<Emoji name="ticket" size={28} />} title="신청한 행사" href="/me/events" chevron />
            <ListRow
              left={<Emoji name="bell" size={28} />}
              title="알림"
              href="/notifications"
              chevron
              right={unread > 0 ? <span className="tabular text-t6 font-semibold text-primary-ink">{unread}개</span> : undefined}
            />
            <ListRow left={<Emoji name="pencil" size={28} />} title="내 정보 고치기" href="/me/edit" chevron />
            <ListRow left={<Emoji name="lock" size={28} />} title="비밀번호 바꾸기" href="/me/password" chevron />
            {user?.role === "ADMIN" && (
              <ListRow left={<Emoji name="shield" size={28} />} title="회원 관리" href="/admin" chevron />
            )}
          </div>
        </Section>

        <div className="flex justify-center pt-4">
          <button
            type="button"
            className="press rounded-lg px-3 py-2 text-t6 font-medium text-ink-4"
            onClick={async () => {
              const ok = await confirm({ title: "로그아웃할까요?", confirmLabel: "로그아웃" });
              if (!ok) return;
              logout.mutate(undefined, {
                onSettled: () => {
                  toast("로그아웃했어요");
                  router.replace("/");
                },
              });
            }}
          >
            로그아웃
          </button>
        </div>
      </div>
    </>
  );
}
