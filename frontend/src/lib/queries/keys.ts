import { withQuery } from "@/lib/api";
import type {
  BoardCategory,
  ContentCategory,
  ContentType,
  EventType,
  QuestionCategory,
  QuestionStatus,
} from "@/lib/types";
import { PAGE_SIZE } from "./paging";

/*
 * 쿼리 키와 요청 경로. 서버 컴포넌트가 미리 가져온 데이터를 브라우저 훅이 그대로 이어받으려면
 * 둘이 같은 키를 써야 하므로 "use client" 가 없는 이 모듈에 모아 둔다.
 * 경로는 Gateway 기준(/api/...)이라 브라우저(BFF)와 서버(Gateway 직접) 양쪽에서 같다.
 */

type ContentFilter = { category?: ContentCategory | null; type?: ContentType | null };
type QuestionFilter = { category?: QuestionCategory | null; status?: QuestionStatus | null };

export const boardsQuery = (category?: BoardCategory | null, size = PAGE_SIZE) => ({
  key: ["boards", { category: category ?? null, size }] as const,
  path: (page: number) => withQuery("/api/boards", { page, size, category }),
});

export const boardQuery = (id: number) => ({ key: ["board", id] as const, path: `/api/boards/${id}` });
export const commentsQuery = (boardId: number) => ({
  key: ["comments", boardId] as const,
  path: `/api/boards/${boardId}/comments`,
});

// 타일은 한 장이 커서 첫 쪽을 작게 — 처음 그리고 하이드레이션할 양을 줄인다
export const CONTENT_PAGE_SIZE = 12;

export const contentsQuery = (filter: ContentFilter, size = CONTENT_PAGE_SIZE) => ({
  key: ["contents", { category: filter.category ?? null, type: filter.type ?? null, size }] as const,
  path: (page: number) => withQuery("/api/contents", { page, size, category: filter.category, type: filter.type }),
});

export const contentQuery = (id: number) => ({ key: ["content", id] as const, path: `/api/contents/${id}` });

export const questionsQuery = (filter: QuestionFilter, size = PAGE_SIZE) => ({
  key: ["questions", { category: filter.category ?? null, status: filter.status ?? null, size }] as const,
  path: (page: number) =>
    withQuery("/api/questions", { page, size, category: filter.category, status: filter.status }),
});

export const questionQuery = (id: number) => ({ key: ["question", id] as const, path: `/api/questions/${id}` });

export const eventsQuery = (type?: EventType | null, size = PAGE_SIZE) => ({
  key: ["events", { type: type ?? null, size }] as const,
  path: (page: number) => withQuery("/api/events", { page, size, type }),
});

export const eventQuery = (id: number) => ({ key: ["event", id] as const, path: `/api/events/${id}` });

// 질문 목록의 상태 필터로 고를 수 있는 값
export const QUESTION_STATUS_FILTERS = ["OPEN", "ANSWERED"] as const;

// 홈 화면이 쓰는 목록 크기
export const HOME = { contents: 6, openQuestions: 3, events: 8, boards: 3 } as const;
