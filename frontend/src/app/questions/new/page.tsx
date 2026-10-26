import type { Metadata } from "next";
import { Fresh } from "@/components/shell/Fresh";
import { QuestionForm } from "../question-form";

export const metadata: Metadata = { title: "질문하기" };

export default function NewQuestionPage() {
  return (
    <Fresh>
      <QuestionForm />
    </Fresh>
  );
}
