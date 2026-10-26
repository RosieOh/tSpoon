// 백엔드 DTO(services/*/dto) 와 1:1 로 맞춘 타입.
// Instant 는 UTC ISO 문자열("...Z"), LocalDateTime 은 한국 현지 시각 문자열("2026-11-20T14:00:00") 이다.

export type Page<T> = {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
};

export type ProblemDetail = {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  errors?: Record<string, string>;
  /** 업무 오류 원인 코드 — EMAIL_NOT_VERIFIED, LOGIN_LOCKED, RATE_LIMITED, ACCOUNT_DISABLED, INVALID_CURRENT_PASSWORD */
  code?: string;
};

// user-service
export type UserRole = "PARENT" | "TEACHER" | "EXPERT" | "ADMIN";
export type UserStatus = "ACTIVE" | "INACTIVE" | "SUSPENDED";

export type User = {
  id: number;
  email: string;
  name: string;
  phone: string | null;
  address: string | null;
  role: UserRole;
  status: UserStatus;
  emailVerified: boolean;
  createdAt: string;
  updatedAt: string;
};

export type Grade =
  | "KINDERGARTEN"
  | "ELEMENTARY_1"
  | "ELEMENTARY_2"
  | "ELEMENTARY_3"
  | "ELEMENTARY_4"
  | "ELEMENTARY_5"
  | "ELEMENTARY_6"
  | "MIDDLE_1"
  | "MIDDLE_2"
  | "MIDDLE_3"
  | "HIGH_1"
  | "HIGH_2"
  | "HIGH_3";

export type Child = {
  id: number;
  name: string;
  birthDate: string | null;
  grade: Grade;
  school: string | null;
  interests: string[];
  notes: string | null;
};

export type ChildInput = {
  name: string;
  birthDate?: string | null;
  grade: Grade;
  school?: string | null;
  interests?: string[];
  notes?: string | null;
};

export type SessionUser = { userId: number; email: string; role: UserRole };

// community-service
export type BoardCategory = "GENERAL" | "EDUCATION" | "PARENTING" | "REVIEW" | "QUESTION" | "ANNOUNCEMENT";

export type Board = {
  id: number;
  title: string;
  content: string;
  authorId: number;
  authorName: string;
  category: BoardCategory;
  status: "ACTIVE" | "DELETED" | "HIDDEN";
  tags: string | null;
  attachments: string | null;
  viewCount: number;
  likeCount: number;
  commentCount: number;
  createdAt: string;
  updatedAt: string;
};

export type BoardInput = {
  title: string;
  content: string;
  category: BoardCategory;
  tags?: string | null;
};

export type Comment = {
  id: number;
  parentCommentId: number | null;
  content: string;
  authorId: number | null;
  authorName: string | null;
  /** 공개 중이 아님 (삭제 또는 숨김) */
  deleted: boolean;
  /** 신고·운영 정책으로 숨겨짐 */
  hidden?: boolean;
  createdAt: string;
  updatedAt: string;
  replies: Comment[];
};

// education-service
export type ContentType = "ARTICLE" | "VIDEO" | "PDF" | "INTERACTIVE" | "QUIZ" | "WORKSHEET";
export type ContentCategory =
  | "EARLY_CHILDHOOD"
  | "ELEMENTARY"
  | "MIDDLE_SCHOOL"
  | "HIGH_SCHOOL"
  | "PARENTING"
  | "SPECIAL_EDUCATION"
  | "LANGUAGE"
  | "MATH"
  | "SCIENCE"
  | "ART"
  | "PHYSICAL"
  | "SOCIAL"
  | "HISTORY"
  | "GEOGRAPHY"
  | "COMPUTER"
  | "MUSIC"
  | "OTHER";

export type ContentSummary = {
  id: number;
  title: string;
  description: string;
  authorName: string;
  type: ContentType;
  category: ContentCategory;
  thumbnailUrl: string | null;
  estimatedTime: number | null;
  targetAge: string | null;
  viewCount: number;
  bookmarkCount: number;
  createdAt: string;
};

export type Content = ContentSummary & {
  content: string | null;
  authorId: number;
  status: "ACTIVE" | "DRAFT" | "HIDDEN" | "DELETED";
  tags: string | null;
  attachments: string | null;
  updatedAt: string;
};

export type ContentInput = {
  title: string;
  description: string;
  content?: string | null;
  type: ContentType;
  category: ContentCategory;
  tags?: string | null;
  thumbnailUrl?: string | null;
  estimatedTime?: number | null;
  targetAge?: string | null;
  draft: boolean;
};

// qna-service
export type QuestionCategory =
  | "GENERAL"
  | "EDUCATION"
  | "PARENTING"
  | "SCHOOL"
  | "LEARNING"
  | "BEHAVIOR"
  | "DEVELOPMENT"
  | "SPECIAL_NEEDS"
  | "TECHNOLOGY"
  | "OTHER";
export type QuestionStatus = "OPEN" | "ANSWERED" | "CLOSED" | "DELETED";
export type Priority = "LOW" | "NORMAL" | "HIGH" | "URGENT";
export type AnswerType = "GENERAL" | "EXPERT" | "TEACHER" | "PARENT";

export type QuestionSummary = {
  id: number;
  title: string;
  authorName: string;
  category: QuestionCategory;
  status: QuestionStatus;
  priority: Priority;
  viewCount: number;
  answerCount: number;
  accepted: boolean;
  createdAt: string;
};

export type Answer = {
  id: number;
  questionId: number;
  content: string;
  authorId: number;
  authorName: string;
  type: AnswerType;
  attachments: string | null;
  accepted: boolean;
  createdAt: string;
  updatedAt: string;
};

export type Question = {
  id: number;
  title: string;
  content: string;
  authorId: number;
  authorName: string;
  category: QuestionCategory;
  status: QuestionStatus;
  priority: Priority;
  tags: string | null;
  attachments: string | null;
  viewCount: number;
  answerCount: number;
  acceptedAnswerId: number | null;
  createdAt: string;
  updatedAt: string;
  answers: Answer[];
};

export type QuestionInput = {
  title: string;
  content: string;
  category: QuestionCategory;
  priority?: Priority;
  tags?: string | null;
};

// event-service
export type EventType =
  | "SEMINAR"
  | "WORKSHOP"
  | "CONFERENCE"
  | "WEBINAR"
  | "MEETUP"
  | "COMPETITION"
  | "EXHIBITION"
  | "CAMP"
  | "CLASS"
  | "OTHER";
export type EventStatus = "ACTIVE" | "CANCELLED" | "COMPLETED" | "POSTPONED" | "FULL";

export type TspoonEvent = {
  id: number;
  title: string;
  description: string;
  organizerId: number;
  organizerName: string;
  type: EventType;
  status: EventStatus;
  startDate: string;
  endDate: string;
  registrationStartDate: string | null;
  registrationEndDate: string | null;
  location: string | null;
  onlineLink: string | null;
  maxParticipants: number | null;
  currentParticipants: number;
  remainingSeats: number | null;
  imageUrl: string | null;
  tags: string | null;
  requirements: string | null;
  contactInfo: string | null;
  createdAt: string;
  updatedAt: string;
};

export type EventInput = {
  title: string;
  description: string;
  type: EventType;
  startDate: string;
  endDate: string;
  registrationStartDate?: string | null;
  registrationEndDate?: string | null;
  location?: string | null;
  onlineLink?: string | null;
  maxParticipants?: number | null;
  requirements?: string | null;
  contactInfo?: string | null;
};

export type RegistrationStatus = "REGISTERED" | "CANCELLED" | "ATTENDED" | "NO_SHOW";

export type Registration = {
  id: number;
  eventId: number;
  eventTitle: string;
  eventStartDate: string;
  userId: number;
  userName: string;
  status: RegistrationStatus;
  notes: string | null;
  contactInfo: string | null;
  createdAt: string;
};

// notification-service
export type NotificationType = "SYSTEM" | "COMMUNITY" | "EDUCATION" | "QNA" | "EVENT" | "REMINDER" | "ANNOUNCEMENT";

export type Notification = {
  id: number;
  title: string;
  content: string;
  type: NotificationType;
  channel: "IN_APP" | "EMAIL" | "SMS" | "PUSH" | "WEBHOOK";
  status: string;
  read: boolean;
  actionUrl: string | null;
  relatedEntityType: string | null;
  relatedEntityId: number | null;
  createdAt: string;
  sentAt: string | null;
  readAt: string | null;
};
