import type { NextConfig } from "next";
import { HTML_LIMITED_BOT_UA_RE } from "next/dist/shared/lib/router/utils/html-bots";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  poweredByHeader: false,
  // JS 를 실행하지 않는 수집기에는 상세 화면의 제목·설명을 <head> 에 담아 보낸다 (나머지에는 본문으로 스트리밍).
  // Next 기본 목록(구글·빙·네이버 Yeti·페이스북/카카오톡 미리보기 등)에 다음(Daum)과 카카오 단독 UA 를 더한다
  htmlLimitedBots: new RegExp(`${HTML_LIMITED_BOT_UA_RE.source}|Daum|Daumoa|kakaotalk-scrap|Kakao`, "i"),
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
