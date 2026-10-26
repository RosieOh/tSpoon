import type {
  AnswerType,
  BoardCategory,
  ContentCategory,
  ContentType,
  EventStatus,
  EventType,
  Grade,
  NotificationType,
  Priority,
  QuestionCategory,
  QuestionStatus,
  RegistrationStatus,
  UserRole,
  UserStatus,
} from "./types";

// 사용자에게 보이는 이름과 아이콘. 서버 enum 이름은 화면에 내보내지 않는다.

export type Visual = { emoji: string; tint: string };

// 천재교육 CI(그린·블루·스카이블루)와 천재 교과서 표지에서 쓰는 원색에 맞춘 카테고리 색.
// 표지처럼 이 색을 면 전체에 깔 때 글자색은 coverInk 가 대비로 고른다
export const HUE = {
  green: "#009944",
  blue: "#265c99",
  sky: "#008ccf",
  yellow: "#ffc80a",
  orange: "#e5631a",
  teal: "#16a29d",
  coral: "#ef5a4f",
  purple: "#7e5bd6",
  pink: "#e8508a",
  grey: "#8b95a1",
} as const;

export const roleLabel: Record<UserRole, string> = {
  PARENT: "학부모",
  TEACHER: "선생님",
  EXPERT: "전문가",
  ADMIN: "관리자",
};

export const userStatusLabel: Record<UserStatus, string> = {
  ACTIVE: "활성",
  INACTIVE: "비활성",
  SUSPENDED: "정지",
};

export const gradeLabel: Record<Grade, string> = {
  KINDERGARTEN: "유치원",
  ELEMENTARY_1: "초등 1학년",
  ELEMENTARY_2: "초등 2학년",
  ELEMENTARY_3: "초등 3학년",
  ELEMENTARY_4: "초등 4학년",
  ELEMENTARY_5: "초등 5학년",
  ELEMENTARY_6: "초등 6학년",
  MIDDLE_1: "중등 1학년",
  MIDDLE_2: "중등 2학년",
  MIDDLE_3: "중등 3학년",
  HIGH_1: "고등 1학년",
  HIGH_2: "고등 2학년",
  HIGH_3: "고등 3학년",
};

export const boardCategory: Record<BoardCategory, { label: string } & Visual> = {
  GENERAL: { label: "자유", emoji: "speech", tint: HUE.sky },
  EDUCATION: { label: "교육 정보", emoji: "bulb", tint: HUE.yellow },
  PARENTING: { label: "육아", emoji: "baby-bottle", tint: HUE.pink },
  REVIEW: { label: "후기", emoji: "star", tint: HUE.orange },
  QUESTION: { label: "질문", emoji: "thinking", tint: HUE.purple },
  ANNOUNCEMENT: { label: "공지", emoji: "megaphone", tint: HUE.coral },
};

export const contentType: Record<ContentType, { label: string } & Visual> = {
  ARTICLE: { label: "아티클", emoji: "newspaper", tint: HUE.sky },
  VIDEO: { label: "영상", emoji: "clapper", tint: HUE.coral },
  PDF: { label: "PDF", emoji: "page", tint: HUE.orange },
  INTERACTIVE: { label: "활동", emoji: "puzzle", tint: HUE.green },
  QUIZ: { label: "퀴즈", emoji: "question", tint: HUE.purple },
  WORKSHEET: { label: "학습지", emoji: "memo", tint: HUE.teal },
};

export const contentCategory: Record<ContentCategory, { label: string } & Visual> = {
  EARLY_CHILDHOOD: { label: "유아", emoji: "chick", tint: HUE.yellow },
  ELEMENTARY: { label: "초등", emoji: "backpack", tint: HUE.sky },
  MIDDLE_SCHOOL: { label: "중등", emoji: "school", tint: HUE.blue },
  HIGH_SCHOOL: { label: "고등", emoji: "graduation", tint: HUE.purple },
  PARENTING: { label: "육아", emoji: "baby-bottle", tint: HUE.pink },
  SPECIAL_EDUCATION: { label: "특수교육", emoji: "seedling", tint: HUE.green },
  LANGUAGE: { label: "언어", emoji: "abc", tint: HUE.coral },
  MATH: { label: "수학", emoji: "abacus", tint: HUE.orange },
  SCIENCE: { label: "과학", emoji: "microscope", tint: HUE.teal },
  ART: { label: "미술", emoji: "palette", tint: HUE.pink },
  PHYSICAL: { label: "체육", emoji: "soccer", tint: HUE.green },
  SOCIAL: { label: "사회", emoji: "busts", tint: HUE.blue },
  HISTORY: { label: "역사", emoji: "scroll", tint: HUE.orange },
  GEOGRAPHY: { label: "지리", emoji: "globe", tint: HUE.teal },
  COMPUTER: { label: "코딩", emoji: "laptop", tint: HUE.sky },
  MUSIC: { label: "음악", emoji: "music", tint: HUE.purple },
  OTHER: { label: "기타", emoji: "sparkles", tint: HUE.grey },
};

export const questionCategory: Record<QuestionCategory, { label: string } & Visual> = {
  GENERAL: { label: "일반", emoji: "speech", tint: HUE.sky },
  EDUCATION: { label: "교육", emoji: "books", tint: HUE.blue },
  PARENTING: { label: "육아", emoji: "baby-bottle", tint: HUE.pink },
  SCHOOL: { label: "학교생활", emoji: "school", tint: HUE.orange },
  LEARNING: { label: "공부법", emoji: "pencil", tint: HUE.yellow },
  BEHAVIOR: { label: "행동", emoji: "thinking", tint: HUE.purple },
  DEVELOPMENT: { label: "발달", emoji: "seedling", tint: HUE.green },
  SPECIAL_NEEDS: { label: "특수교육", emoji: "sprout", tint: HUE.teal },
  TECHNOLOGY: { label: "디지털", emoji: "laptop", tint: HUE.sky },
  OTHER: { label: "기타", emoji: "sparkles", tint: HUE.grey },
};

export const questionStatus: Record<QuestionStatus, string> = {
  OPEN: "답변 기다리는 중",
  ANSWERED: "답변 완료",
  CLOSED: "마감",
  DELETED: "삭제됨",
};

export const priorityLabel: Record<Priority, string> = {
  LOW: "천천히",
  NORMAL: "보통",
  HIGH: "빨리",
  URGENT: "긴급",
};

export const answerType: Record<AnswerType, { label: string; emoji: string } | null> = {
  EXPERT: { label: "전문가 답변", emoji: "graduation" },
  TEACHER: { label: "선생님 답변", emoji: "pencil" },
  PARENT: null,
  GENERAL: null,
};

export const eventType: Record<EventType, { label: string } & Visual> = {
  SEMINAR: { label: "세미나", emoji: "microphone", tint: HUE.blue },
  WORKSHOP: { label: "워크숍", emoji: "tools", tint: HUE.orange },
  CONFERENCE: { label: "컨퍼런스", emoji: "building", tint: HUE.sky },
  WEBINAR: { label: "웨비나", emoji: "desktop", tint: HUE.teal },
  MEETUP: { label: "모임", emoji: "coffee", tint: HUE.pink },
  COMPETITION: { label: "대회", emoji: "trophy", tint: HUE.yellow },
  EXHIBITION: { label: "전시", emoji: "picture", tint: HUE.purple },
  CAMP: { label: "캠프", emoji: "tent", tint: HUE.green },
  CLASS: { label: "수업", emoji: "books", tint: HUE.sky },
  OTHER: { label: "기타", emoji: "party", tint: HUE.grey },
};

export const eventStatus: Record<EventStatus, string> = {
  ACTIVE: "모집 중",
  CANCELLED: "취소됨",
  COMPLETED: "종료",
  POSTPONED: "연기됨",
  FULL: "마감",
};

export const registrationStatus: Record<RegistrationStatus, string> = {
  REGISTERED: "신청 완료",
  CANCELLED: "신청 취소",
  ATTENDED: "참석",
  NO_SHOW: "불참",
};

export const notificationType: Record<NotificationType, Visual> = {
  SYSTEM: { emoji: "bell", tint: HUE.grey },
  COMMUNITY: { emoji: "speech", tint: HUE.sky },
  EDUCATION: { emoji: "books", tint: HUE.blue },
  QNA: { emoji: "raising-hand", tint: HUE.purple },
  EVENT: { emoji: "calendar", tint: HUE.orange },
  REMINDER: { emoji: "hourglass", tint: HUE.yellow },
  ANNOUNCEMENT: { emoji: "megaphone", tint: HUE.coral },
};

export function keysOf<T extends string>(record: Record<T, unknown>): T[] {
  return Object.keys(record) as T[];
}

// "주말,체험" → ["주말", "체험"]
export function splitTags(tags: string | null | undefined): string[] {
  if (!tags) return [];
  return tags
    .split(/[,#\s]+/)
    .map((t) => t.trim())
    .filter(Boolean);
}

function luminance(hex: string) {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** 색 면 위의 큰 글자색 — 흰 글자가 큰 글자 기준(3:1)을 넘으면 흰색, 아니면 진한 글자 */
export function coverInk(hex: string) {
  return 1.05 / (luminance(hex) + 0.05) >= 3 ? "#ffffff" : "#191f28";
}
