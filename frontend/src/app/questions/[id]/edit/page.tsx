"use client";

import { useParams } from "next/navigation";
import { Suspense } from "react";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { DetailScreenSkeleton } from "@/components/shell/Screen";
import { ErrorState } from "@/components/ui/States";
import { useQuestion } from "@/lib/queries/questions";
import { QuestionForm } from "../../question-form";

export default function EditQuestionPage() {
  return (
    <Suspense fallback={<DetailScreenSkeleton fallback="/questions" />}>
      <EditQuestion />
    </Suspense>
  );
}

function EditQuestion() {
  const { id } = useParams<{ id: string }>();
  const question = useQuestion(Number(id));
  if (question.isPending) return <DetailScreenSkeleton fallback={`/questions/${id}`} />;
  if (question.isError) {
    return (
      <>
        <NavBar left={<BackButton fallback="/questions" />} />
        <ErrorState error={question.error} onRetry={() => question.refetch()} />
      </>
    );
  }
  return <QuestionForm key={question.data.id} question={question.data} />;
}
