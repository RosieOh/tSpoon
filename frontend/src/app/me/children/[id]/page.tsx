"use client";

import { useParams } from "next/navigation";
import { Suspense } from "react";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { DetailScreenSkeleton } from "@/components/shell/Screen";
import { EmptyState } from "@/components/ui/States";
import { useChildren, useSession } from "@/lib/queries/session";
import { ChildForm } from "../child-form";

export default function EditChildPage() {
  return (
    <Suspense fallback={<DetailScreenSkeleton fallback="/me" />}>
      <EditChild />
    </Suspense>
  );
}

// 자녀 단건 조회 API 가 없어 목록에서 찾는다
function EditChild() {
  const { id } = useParams<{ id: string }>();
  const { user } = useSession();
  const children = useChildren(user !== null);
  if (children.isPending) return <DetailScreenSkeleton fallback="/me" />;
  const child = children.data?.find((c) => c.id === Number(id));
  if (!child) {
    return (
      <>
        <NavBar left={<BackButton fallback="/me" />} />
        <EmptyState emoji="search" title="아이 정보를 찾을 수 없어요" />
      </>
    );
  }
  return <ChildForm key={child.id} child={child} />;
}
