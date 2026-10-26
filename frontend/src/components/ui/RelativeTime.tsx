import { relativeTime } from "@/lib/format";

/**
 * "5분 전" 같은 상대 시각. 서버가 그린 시각과 브라우저가 이어받는 시각이 몇 초 달라
 * 경계("방금 전" ↔ "1분 전")에서 글자가 바뀔 수 있으므로 그 차이는 경고하지 않는다.
 */
export function RelativeTime({ iso }: { iso: string }) {
  return (
    <time dateTime={iso} suppressHydrationWarning>
      {relativeTime(iso)}
    </time>
  );
}
