import {
  BookOpen,
  CalendarDays,
  House,
  MessageCircleQuestionMark,
  MessagesSquare,
  PenLine,
  Plus,
  type LucideIcon,
} from "lucide-react";
import type { UserRole } from "@/lib/types";

export type Tab = { href: string; label: string; icon: LucideIcon };

export const TABS: Tab[] = [
  { href: "/", label: "홈", icon: House },
  { href: "/boards", label: "커뮤니티", icon: MessagesSquare },
  { href: "/contents", label: "배움", icon: BookOpen },
  { href: "/questions", label: "질문", icon: MessageCircleQuestionMark },
  { href: "/events", label: "행사", icon: CalendarDays },
];

export type TabAction = {
  href: string;
  label: string;
  icon: LucideIcon;
  /** 로그인하지 않았을 때 안내 문구 */
  reason: string;
  /** 정해진 역할만 보이는 동작 */
  roles?: UserRole[];
};

const CREATORS: UserRole[] = ["TEACHER", "EXPERT", "ADMIN"];

export const TAB_ACTIONS: Record<string, TabAction> = {
  "/boards": { href: "/boards/new", label: "글쓰기", icon: PenLine, reason: "글을 쓰려면" },
  "/contents": { href: "/contents/new", label: "콘텐츠 만들기", icon: Plus, reason: "콘텐츠를 만들려면", roles: CREATORS },
  "/questions": { href: "/questions/new", label: "질문하기", icon: PenLine, reason: "질문하려면" },
  "/events": { href: "/events/new", label: "행사 열기", icon: Plus, reason: "행사를 열려면", roles: CREATORS },
};

export function activeTabHref(pathname: string | null): string | null {
  if (!pathname) return null;
  if (pathname === "/") return "/";
  return TABS.find((t) => t.href !== "/" && (pathname === t.href || pathname.startsWith(`${t.href}/`)))?.href ?? null;
}

export function isTabRoot(pathname: string | null) {
  return TABS.some((t) => t.href === pathname);
}

export function actionVisible(action: TabAction | undefined, role: UserRole | undefined) {
  if (!action) return false;
  if (!action.roles) return true;
  return role !== undefined && action.roles.includes(role);
}
