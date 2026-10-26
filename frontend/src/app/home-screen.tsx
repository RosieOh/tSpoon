"use client";

import { Clock, Plus } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { BrandLogo, BrandMark } from "@/components/brand/BrandMark";
import { LiquidGlass } from "@/components/glass/LiquidGlass";
import { BoardRow } from "@/components/domain/BoardRow";
import { ContentArt, ContentRow } from "@/components/domain/ContentArt";
import { EventMiniCard } from "@/components/domain/EventCard";
import { QuestionRow } from "@/components/domain/QuestionRow";
import { useOverlay } from "@/components/overlays/Overlays";
import { AccountActions, NavBar } from "@/components/shell/NavBar";
import { Emoji } from "@/components/ui/Emoji";
import { Section } from "@/components/ui/Section";
import { EmptyState, ErrorState, SkeletonBlock, SkeletonRows } from "@/components/ui/States";
import { ageOf } from "@/lib/format";
import { contentType, gradeLabel } from "@/lib/labels";
import { useBoards } from "@/lib/queries/boards";
import { useContents } from "@/lib/queries/contents";
import { useEvents } from "@/lib/queries/events";
import { HOME } from "@/lib/queries/keys";
import { flatten } from "@/lib/queries/paging";
import { useQuestions } from "@/lib/queries/questions";
import { useChildren, useSession } from "@/lib/queries/session";
import type { ContentSummary } from "@/lib/types";

function HomeNavBar() {
  return (
    <NavBar
      left={
        <Link href="/" aria-label="티스푼 홈" className="px-1 lg:hidden">
          <BrandLogo size={30} />
        </Link>
      }
      right={<AccountActions />}
    />
  );
}

/**
 * 데이터가 오기 전 뼈대. 목록 쿼리 훅을 부르지 않는다 —
 * 같은 QueryClient 에 빈 쿼리를 먼저 만들면 서버에서 HydrationBoundary 가 데이터를 렌더 중에 채우지 못한다.
 */
export function HomeSkeleton() {
  return (
    <>
      <HomeNavBar />
      <Greeting />
      <div className="space-y-3">
        <SkeletonBlock className="mx-3 h-[320px] rounded-section" />
        <QuickActions />
        <Section title="답변을 기다리는 질문" description="경험을 나눠 주세요">
          <SkeletonRows count={3} icon={false} />
        </Section>
      </div>
    </>
  );
}

export function HomeScreen() {
  return (
    <>
      <HomeNavBar />
      <Greeting />
      <div className="space-y-3">
        <TodaySpoon />
        <QuickActions />
        <WaitingQuestions />
        <UpcomingEvents />
        <LatestBoards />
        <MoreContents />
      </div>
    </>
  );
}

function Greeting() {
  const { user, isLoading } = useSession();
  const children = useChildren(user !== null);

  if (isLoading) {
    return (
      <div className="space-y-2.5 px-5 pt-3 pb-6">
        <SkeletonBlock className="h-8 w-48" />
        <SkeletonBlock className="h-8 w-64" />
      </div>
    );
  }

  if (!user) {
    return (
      <div className="px-5 pt-3 pb-6">
        <h1 className="text-t1 text-ink">
          아이 교육,
          <br />
          혼자 고민하지 마세요
        </h1>
        <p className="mt-2 text-t5 text-ink-3">학부모, 선생님, 전문가가 한 스푼씩 나눠요</p>
      </div>
    );
  }

  return (
    <div className="pt-3 pb-6">
      <h1 className="px-5 text-t1 text-ink">
        {user.name}님,
        <br />
        오늘도 한 스푼씩 알아가요
      </h1>
      {children.data && (
        <ul aria-label="우리 아이" className="no-scrollbar mt-4 flex gap-2 overflow-x-auto px-5">
          {children.data.map((child) => {
            const age = ageOf(child.birthDate);
            return (
              <li key={child.id} className="shrink-0">
                <Link
                  href={`/me/children/${child.id}`}
                  className="press flex items-center gap-2 rounded-full bg-surface py-1.5 pr-4 pl-1.5"
                >
                  <span className="grid size-8 place-items-center rounded-full bg-fill">
                    <Emoji name="chick" size={22} />
                  </span>
                  <span className="text-t6 font-semibold text-ink">{child.name}</span>
                  <span className="text-t7 text-ink-3">{gradeLabel[child.grade]}</span>
                  {age !== null && <span className="tabular text-t7 text-ink-4">만 {age}세</span>}
                </Link>
              </li>
            );
          })}
          <li className="shrink-0">
            <Link
              href="/me/children/new"
              className="press flex h-11 items-center gap-1.5 rounded-full border border-dashed border-ink-5 px-4 text-t6 font-semibold text-ink-3"
            >
              <Plus className="size-4" strokeWidth={2.4} aria-hidden />
              {children.data.length === 0 ? "우리 아이 등록하기" : "아이 추가"}
            </Link>
          </li>
        </ul>
      )}
    </div>
  );
}

function TodaySpoon() {
  const contents = useContents({}, HOME.contents);
  const first = flatten(contents.data)[0];

  if (contents.isPending) return <SkeletonBlock className="mx-3 h-[320px] rounded-section" />;
  if (contents.isError) return <ErrorState error={contents.error} onRetry={() => contents.refetch()} />;
  if (!first) return null;
  return <TodaySpoonCard content={first} />;
}

function TodaySpoonCard({ content }: { content: ContentSummary }) {
  const type = contentType[content.type];
  return (
    <Link
      href={`/contents/${content.id}`}
      className="press relative mx-3 block h-[320px] overflow-hidden rounded-section"
    >
      <ContentArt content={content} variant="hero" eager />
      <span className="glass glass-on-cover absolute top-4 left-4 flex items-center gap-1.5 rounded-full py-1.5 pr-3 pl-2 text-t7 font-bold [--glass-blur:10px]">
        <BrandMark size={18} className="relative z-[2]" />
        <span className="relative z-[2]">오늘의 한 스푼</span>
      </span>
      <LiquidGlass radius={22} bezel={20} strength={70} blur={6} className="absolute inset-x-3 bottom-3 p-4">
        <span className="relative z-[2] flex items-center gap-2 text-t7 font-medium text-ink-2">
          <span>{type.label}</span>
          {content.estimatedTime && (
            <span className="tabular flex items-center gap-0.5">
              <Clock className="size-3" aria-hidden />
              {content.estimatedTime}분
            </span>
          )}
          {content.targetAge && <span>{content.targetAge}</span>}
        </span>
        <span className="relative z-[2] mt-1 line-clamp-2 block text-t3 font-bold text-ink">{content.title}</span>
      </LiquidGlass>
    </Link>
  );
}

const QUICK = [
  { emoji: "raising-hand", label: "질문하기", href: "/questions/new", reason: "질문하려면" },
  { emoji: "pencil", label: "글쓰기", href: "/boards/new", reason: "글을 쓰려면" },
  { emoji: "bookmark", label: "담아둔 배움", href: "/me/bookmarks", reason: "담아둔 콘텐츠를 보려면" },
  { emoji: "ticket", label: "신청한 행사", href: "/me/events", reason: "신청한 행사를 보려면" },
];

function QuickActions() {
  const router = useRouter();
  const { requireAuth } = useOverlay();
  return (
    <nav aria-label="바로가기" className="mx-3 grid grid-cols-4 rounded-section bg-surface px-2 py-4">
      {QUICK.map((item) => (
        <button
          key={item.href}
          type="button"
          onClick={() => requireAuth(item.reason) && router.push(item.href)}
          className="press flex flex-col items-center gap-2 rounded-2xl py-1"
        >
          <span className="grid size-14 place-items-center rounded-[18px] bg-fill">
            <Emoji name={item.emoji} size={36} />
          </span>
          <span className="text-t7 font-medium text-ink-2">{item.label}</span>
        </button>
      ))}
    </nav>
  );
}

function WaitingQuestions() {
  const questions = useQuestions({ status: "OPEN" }, HOME.openQuestions);
  const items = flatten(questions.data);
  return (
    <Section
      title="답변을 기다리는 질문"
      description="경험을 나눠 주세요"
      more={{ href: "/questions?status=OPEN" }}
      className="cv-section"
    >
      {questions.isPending ? (
        <SkeletonRows count={3} icon={false} />
      ) : items.length === 0 ? (
        <EmptyState emoji="check" title="모든 질문에 답했어요" className="py-8" />
      ) : (
        items.map((q) => <QuestionRow key={q.id} question={q} />)
      )}
    </Section>
  );
}

function UpcomingEvents() {
  const events = useEvents(null, HOME.events);
  const items = flatten(events.data).filter((e) => e.status === "ACTIVE");
  if (!events.isPending && items.length === 0) return null;
  return (
    <Section title="다가오는 행사" more={{ href: "/events" }} bodyClassName="pb-5" className="cv-section">
      <div className="no-scrollbar flex gap-2.5 overflow-x-auto px-5 pt-2">
        {events.isPending
          ? Array.from({ length: 3 }, (_, i) => <SkeletonBlock key={i} className="h-[164px] w-[208px] shrink-0 rounded-[20px]" />)
          : items.map((e) => <EventMiniCard key={e.id} event={e} />)}
      </div>
    </Section>
  );
}

function LatestBoards() {
  const boards = useBoards(null, HOME.boards);
  const items = flatten(boards.data);
  return (
    <Section title="방금 올라온 이야기" more={{ href: "/boards" }} className="cv-section">
      {boards.isPending ? (
        <SkeletonRows count={3} icon={false} />
      ) : items.length === 0 ? (
        <EmptyState emoji="speech" title="아직 글이 없어요" description="첫 이야기를 들려주세요" className="py-8" />
      ) : (
        items.map((b) => <BoardRow key={b.id} board={b} />)
      )}
    </Section>
  );
}

function MoreContents() {
  const contents = useContents({}, HOME.contents);
  const items = flatten(contents.data).slice(1, 5);
  if (items.length === 0) return null;
  return (
    <Section title="새로 올라온 배움" more={{ href: "/contents" }} className="cv-section">
      {items.map((c) => (
        <ContentRow key={c.id} content={c} />
      ))}
    </Section>
  );
}
