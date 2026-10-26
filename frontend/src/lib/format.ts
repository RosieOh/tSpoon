// 날짜 표기. 생성·수정 시각(Instant)은 UTC 로 오고, 행사 일정(LocalDateTime)은 한국 현지 시각 문자열로 온다.

const TZ = "Asia/Seoul";
const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

type Wall = { year: number; month: number; day: number; hour: number; minute: number; weekday: number };

// 시간대와 상관없이 "벽시계" 값으로 다룬다
function wallFromLocal(local: string): Wall {
  const [date, time = "00:00"] = local.split("T");
  const [year, month, day] = date.split("-").map(Number);
  const [hour, minute] = time.split(":").map(Number);
  const weekday = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
  return { year, month, day, hour, minute, weekday };
}

function wallFromInstant(instant: Date): Wall {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: TZ,
    year: "numeric",
    month: "numeric",
    day: "numeric",
    hour: "numeric",
    minute: "numeric",
    hourCycle: "h23",
  }).formatToParts(instant);
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value);
  const year = get("year");
  const month = get("month");
  const day = get("day");
  return {
    year,
    month,
    day,
    hour: get("hour"),
    minute: get("minute"),
    weekday: new Date(Date.UTC(year, month - 1, day)).getUTCDay(),
  };
}

function dayNumber(w: Pick<Wall, "year" | "month" | "day">) {
  return Math.floor(Date.UTC(w.year, w.month - 1, w.day) / 86_400_000);
}

function minutesOf(w: Wall) {
  return dayNumber(w) * 1440 + w.hour * 60 + w.minute;
}

export function nowWall(): Wall {
  return wallFromInstant(new Date());
}

function time(w: Wall) {
  const meridiem = w.hour < 12 ? "오전" : "오후";
  const h = w.hour % 12 === 0 ? 12 : w.hour % 12;
  return w.minute === 0 ? `${meridiem} ${h}시` : `${meridiem} ${h}:${String(w.minute).padStart(2, "0")}`;
}

/** "방금 전", "5분 전", "3시간 전", "어제", "4일 전", "10월 3일", "2025년 3월 2일" */
export function relativeTime(iso: string): string {
  const then = new Date(iso);
  const diff = (Date.now() - then.getTime()) / 1000;
  if (diff < 60) return "방금 전";
  if (diff < 3600) return `${Math.floor(diff / 60)}분 전`;
  const now = nowWall();
  const w = wallFromInstant(then);
  const days = dayNumber(now) - dayNumber(w);
  if (days === 0) return `${Math.floor(diff / 3600)}시간 전`;
  if (days === 1) return "어제";
  if (days < 7) return `${days}일 전`;
  if (w.year === now.year) return `${w.month}월 ${w.day}일`;
  return `${w.year}년 ${w.month}월 ${w.day}일`;
}

/** 행사 날짜 블록용 */
export function eventDateParts(local: string) {
  const w = wallFromLocal(local);
  return { month: w.month, day: w.day, weekday: WEEKDAYS[w.weekday] };
}

/** "11월 15일 (토) 오후 2시" */
export function eventDateTime(local: string): string {
  const w = wallFromLocal(local);
  return `${w.month}월 ${w.day}일 (${WEEKDAYS[w.weekday]}) ${time(w)}`;
}

/** "11월 15일 (토)" */
export function eventDate(local: string): string {
  const w = wallFromLocal(local);
  return `${w.month}월 ${w.day}일 (${WEEKDAYS[w.weekday]})`;
}

/** 같은 날이면 "오후 2시 ~ 4시", 다른 날이면 "11월 15일 (토) 오후 2시 ~ 11월 17일 (월) 오후 4시" */
export function eventRange(start: string, end: string): string {
  const s = wallFromLocal(start);
  const e = wallFromLocal(end);
  if (dayNumber(s) === dayNumber(e)) return `${eventDate(start)} ${time(s)} ~ ${time(e)}`;
  return `${eventDateTime(start)} ~ ${eventDateTime(end)}`;
}

export function eventTimeRange(start: string, end: string): string {
  const s = wallFromLocal(start);
  const e = wallFromLocal(end);
  if (dayNumber(s) === dayNumber(e)) return `${time(s)} ~ ${time(e)}`;
  return `${s.month}월 ${s.day}일 ~ ${e.month}월 ${e.day}일`;
}

/** 오늘(한국) 기준 남은 날 수. 오늘이면 0, 지났으면 음수 */
export function daysUntil(local: string): number {
  return dayNumber(wallFromLocal(local)) - dayNumber(nowWall());
}

export function dDay(local: string): string {
  const d = daysUntil(local);
  if (d === 0) return "오늘";
  if (d > 0) return `D-${d}`;
  return "지난 행사";
}

/** 현지 시각 문자열이 지금(한국)보다 앞인지 */
export function isPast(local: string): boolean {
  return minutesOf(wallFromLocal(local)) <= minutesOf(nowWall());
}

/** 만 나이 */
export function ageOf(birthDate: string | null): number | null {
  if (!birthDate) return null;
  const b = wallFromLocal(birthDate);
  const now = nowWall();
  let age = now.year - b.year;
  if (now.month < b.month || (now.month === b.month && now.day < b.day)) age -= 1;
  return age;
}

export function compactCount(n: number): string {
  if (n < 1000) return String(n);
  if (n < 10_000) return `${(n / 1000).toFixed(1).replace(/\.0$/, "")}천`;
  return `${(n / 10_000).toFixed(1).replace(/\.0$/, "")}만`;
}
