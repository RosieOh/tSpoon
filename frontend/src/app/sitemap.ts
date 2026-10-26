import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";

// 공개 목록 화면. 개별 글은 목록에서 링크로 이어진다
export default function sitemap(): MetadataRoute.Sitemap {
  return [
    { url: `${SITE_URL}/`, changeFrequency: "hourly", priority: 1 },
    { url: `${SITE_URL}/contents`, changeFrequency: "daily", priority: 0.9 },
    { url: `${SITE_URL}/questions`, changeFrequency: "hourly", priority: 0.8 },
    { url: `${SITE_URL}/boards`, changeFrequency: "hourly", priority: 0.8 },
    { url: `${SITE_URL}/events`, changeFrequency: "daily", priority: 0.7 },
  ];
}
