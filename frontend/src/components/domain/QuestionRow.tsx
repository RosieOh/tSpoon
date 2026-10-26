import clsx from "clsx";
import { Check } from "lucide-react";
import Link from "next/link";
import { RelativeTime } from "@/components/ui/RelativeTime";
import { Badge } from "@/components/ui/Badge";
import { questionCategory } from "@/lib/labels";
import type { QuestionSummary } from "@/lib/types";

export function QuestionRow({ question }: { question: QuestionSummary }) {
  const category = questionCategory[question.category];
  const waiting = question.answerCount === 0;
  return (
    <Link href={`/questions/${question.id}`} className="press-row flex items-center gap-4 px-5 py-4">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5">
          {question.accepted && (
            <Badge tone="green" icon={<Check className="size-3" strokeWidth={3} aria-hidden />}>
              채택
            </Badge>
          )}
          {question.priority === "URGENT" && <Badge tone="red">긴급</Badge>}
          {question.status === "CLOSED" && <Badge>마감</Badge>}
          <span className="text-t7 text-ink-3">{category.label}</span>
        </div>
        <p className="mt-1.5 line-clamp-2 text-t4 font-semibold text-ink">
          <span className="mr-1 text-primary-ink">Q.</span>
          {question.title}
        </p>
        <p className="mt-1.5 flex gap-2 text-t7 text-ink-4">
          <span>{question.authorName}</span>
          <span><RelativeTime iso={question.createdAt} /></span>
        </p>
      </div>
      <div
        className={clsx(
          "flex w-[54px] shrink-0 flex-col items-center justify-center rounded-[14px] py-2",
          waiting ? "bg-fill text-ink-4" : "bg-primary-weak text-primary-weak-ink",
        )}
        aria-label={`답변 ${question.answerCount}개`}
      >
        <span className="tabular text-t3 leading-none font-bold">{question.answerCount}</span>
        <span className="mt-1 text-[11px] font-medium">답변</span>
      </div>
    </Link>
  );
}
