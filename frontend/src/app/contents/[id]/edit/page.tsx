"use client";

import { useParams } from "next/navigation";
import { Suspense } from "react";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { DetailScreenSkeleton } from "@/components/shell/Screen";
import { ErrorState } from "@/components/ui/States";
import { useContent } from "@/lib/queries/contents";
import { ContentForm } from "../../content-form";

export default function EditContentPage() {
  return (
    <Suspense fallback={<DetailScreenSkeleton fallback="/contents" />}>
      <EditContent />
    </Suspense>
  );
}

function EditContent() {
  const { id } = useParams<{ id: string }>();
  const content = useContent(Number(id));
  if (content.isPending) return <DetailScreenSkeleton fallback={`/contents/${id}`} />;
  if (content.isError) {
    return (
      <>
        <NavBar left={<BackButton fallback="/contents" />} />
        <ErrorState error={content.error} onRetry={() => content.refetch()} />
      </>
    );
  }
  return <ContentForm key={content.data.id} content={content.data} />;
}
