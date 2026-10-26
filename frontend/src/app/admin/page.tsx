"use client";

import { useState } from "react";
import { useOverlay } from "@/components/overlays/Overlays";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { Avatar } from "@/components/ui/Avatar";
import { Badge } from "@/components/ui/Badge";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { Segmented } from "@/components/ui/Segmented";
import { EmptyState, ErrorState, LoadMore, SkeletonRows } from "@/components/ui/States";
import { isApiError } from "@/lib/api";
import { keysOf, roleLabel, userStatusLabel } from "@/lib/labels";
import { useAdminUsers, useChangeUser } from "@/lib/queries/admin";
import { flatten, totalOf } from "@/lib/queries/paging";
import { useSession } from "@/lib/queries/session";
import type { User, UserRole, UserStatus } from "@/lib/types";

export default function AdminPage() {
  const { user, isLoading } = useSession();
  const users = useAdminUsers();
  const [selected, setSelected] = useState<User | null>(null);
  const items = flatten(users.data);
  const total = totalOf(users.data);

  if (!isLoading && user?.role !== "ADMIN") {
    return (
      <>
        <NavBar left={<BackButton fallback="/me" />} />
        <EmptyState emoji="shield" title="관리자만 볼 수 있어요" />
      </>
    );
  }

  return (
    <>
      <NavBar left={<BackButton fallback="/me" />} title="회원 관리" />
      <div className="px-5 pt-1 pb-4">
        <h1 className="text-t1 text-ink">회원 관리</h1>
        {total !== undefined && <p className="tabular mt-1 text-t5 text-ink-3">{total}명</p>}
      </div>
      <section className="mx-3 rounded-section bg-surface py-2">
        {users.isPending ? (
          <SkeletonRows count={6} />
        ) : users.isError ? (
          <ErrorState error={users.error} onRetry={() => users.refetch()} />
        ) : (
          items.map((u) => (
            <button
              key={u.id}
              type="button"
              onClick={() => setSelected(u)}
              className="press-row flex w-full items-center gap-3.5 px-5 py-3 text-left"
            >
              <Avatar name={u.name} size={40} />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-t5 font-semibold text-ink">{u.name}</span>
                <span className="block truncate text-t7 text-ink-3">{u.email}</span>
              </span>
              <span className="flex shrink-0 gap-1">
                {u.status !== "ACTIVE" && <Badge tone="red">{userStatusLabel[u.status]}</Badge>}
                {!u.emailVerified && <Badge tone="orange">미인증</Badge>}
                <Badge tone={u.role === "PARENT" ? "grey" : "blue"}>{roleLabel[u.role]}</Badge>
              </span>
            </button>
          ))
        )}
        <LoadMore {...users} />
      </section>
      <MemberSheet member={selected} isSelf={selected?.id === user?.id} onClose={() => setSelected(null)} />
    </>
  );
}

function MemberSheet({ member, isSelf, onClose }: { member: User | null; isSelf: boolean; onClose: () => void }) {
  const change = useChangeUser();
  const { toast, confirm } = useOverlay();

  const apply = async (patch: { role?: UserRole; status?: UserStatus }) => {
    if (!member) return;
    if (patch.status && patch.status !== "ACTIVE") {
      const ok = await confirm({
        title: `${member.name}님을 ${userStatusLabel[patch.status]} 상태로 바꿀까요?`,
        description: "바로 로그아웃되고 다시 로그인할 수 없어요.",
        confirmLabel: "바꾸기",
        danger: true,
      });
      if (!ok) return;
    }
    change.mutate(
      { id: member.id, ...patch },
      {
        onSuccess: () => {
          toast("회원 정보를 바꿨어요");
          onClose();
        },
        onError: (e) => toast(isApiError(e) ? e.userMessage : "바꾸지 못했어요", "error"),
      },
    );
  };

  return (
    <BottomSheet open={member !== null} onClose={onClose} title={member?.name} description={member?.email}>
      {member && (
        <div className="space-y-6">
          {isSelf && <p className="text-t6 text-ink-3">내 계정의 역할과 상태는 여기서 바꿀 수 없어요.</p>}
          <div>
            <p className="mb-2 text-t7 font-medium text-ink-3">역할</p>
            <Segmented
              label="역할"
              options={keysOf(roleLabel).map((r) => ({ value: r, label: roleLabel[r] }))}
              value={member.role}
              onChange={(role) => !isSelf && role !== member.role && apply({ role })}
            />
            <p className="mt-2 text-t7 text-ink-4">바꾸면 지금 쓰는 로그인은 새 역할로 다시 이어져요.</p>
          </div>
          <div>
            <p className="mb-2 text-t7 font-medium text-ink-3">상태</p>
            <Segmented
              label="상태"
              options={keysOf(userStatusLabel).map((s) => ({ value: s, label: userStatusLabel[s] }))}
              value={member.status}
              onChange={(status) => !isSelf && status !== member.status && apply({ status })}
            />
          </div>
        </div>
      )}
    </BottomSheet>
  );
}
