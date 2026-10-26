"use client";

import { useParams } from "next/navigation";
import { Suspense } from "react";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { DetailScreenSkeleton } from "@/components/shell/Screen";
import { ErrorState } from "@/components/ui/States";
import { useBoard } from "@/lib/queries/boards";
import { BoardForm } from "../../board-form";

export default function EditBoardPage() {
  return (
    <Suspense fallback={<DetailScreenSkeleton fallback="/boards" />}>
      <EditBoard />
    </Suspense>
  );
}

function EditBoard() {
  const { id } = useParams<{ id: string }>();
  const board = useBoard(Number(id));
  if (board.isPending) return <DetailScreenSkeleton fallback={`/boards/${id}`} />;
  if (board.isError) {
    return (
      <>
        <NavBar left={<BackButton fallback="/boards" />} />
        <ErrorState error={board.error} onRetry={() => board.refetch()} />
      </>
    );
  }
  return <BoardForm key={board.data.id} board={board.data} />;
}
