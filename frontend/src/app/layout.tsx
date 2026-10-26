import type { Metadata, Viewport } from "next";
import { Suspense } from "react";
import { NavigationTracker } from "@/components/shell/nav-history";
import { SideBar, SideBarFrame } from "@/components/shell/SideBar";
import { TabBar } from "@/components/shell/TabBar";
import { SITE_DESCRIPTION, SITE_NAME, SITE_URL } from "@/lib/site";
// Pretendard — 쓰는 글자 범위만 나눠 받는 dynamic subset 을 같은 출처에서 서빙한다 (외부 CDN 연결·렌더 차단 제거)
import "pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css";
import "./globals.css";
import { Providers } from "./providers";

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: { default: SITE_NAME, template: `%s | ${SITE_NAME}` },
  description: SITE_DESCRIPTION,
  applicationName: SITE_NAME,
  openGraph: { type: "website", locale: "ko_KR", siteName: SITE_NAME, title: SITE_NAME, description: SITE_DESCRIPTION },
  twitter: { card: "summary" },
  appleWebApp: { capable: true, title: SITE_NAME, statusBarStyle: "default" },
  formatDetection: { telephone: false },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f2f4f6" },
    { media: "(prefers-color-scheme: dark)", color: "#101013" },
  ],
};

/*
 * 한글 서브셋 글꼴은 수십 개 파일로 나뉘어 있어, 하나씩 도착할 때마다 화면 전체를 다시 셰이핑하면 메인 스레드가 오래 막힌다.
 * 문서를 다 받으면 화면 글자에 필요한 서브셋만 한 번에 요청하고, 모두 오면 클래스 하나로 한 번에 바꾼다.
 * JS 번들과 상관없이 바로 돌도록 인라인으로 둔다.
 */
const FONT_LOADER = `(() => {
  const root = document.documentElement;
  const ready = () => root.classList.add("fonts-ready");
  if (!document.fonts || !document.fonts.load) return ready();
  const start = () => {
    let text = "";
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
      acceptNode: (n) => (n.parentElement && /^(SCRIPT|STYLE|NOSCRIPT)$/.test(n.parentElement.tagName) ? 2 : 1),
    });
    while (walker.nextNode()) text += walker.currentNode.nodeValue;
    Promise.race([
      document.fonts.load('16px "Pretendard Variable"', text + "가나다ABC123"),
      new Promise((r) => setTimeout(r, 3000)),
    ]).then(ready, ready);
  };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start, { once: true });
  else start();
})();`;

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    // FONT_LOADER 가 하이드레이션 전에 class 를 붙인다
    <html lang="ko" suppressHydrationWarning>
      <body>
        <script dangerouslySetInnerHTML={{ __html: FONT_LOADER }} />
        <Providers>
          <Suspense fallback={<SideBarFrame pathname={null} />}>
            <SideBar />
          </Suspense>
          <div className="lg:pl-[264px]">
            <main className="mx-auto min-h-dvh w-full max-w-[640px] pb-[calc(env(safe-area-inset-bottom)+128px)] lg:pb-16">
              {children}
            </main>
          </div>
          <Suspense fallback={null}>
            <TabBar />
            <NavigationTracker />
          </Suspense>
        </Providers>
      </body>
    </html>
  );
}
