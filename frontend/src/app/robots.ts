import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";

// 공개 화면만 수집하게 한다. 내 정보·관리·작성 화면과 BFF(/api)는 빼고
export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: ["/api/", "/me", "/admin", "/notifications", "/login", "/signup", "/*/new", "/*/edit"],
    },
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}
