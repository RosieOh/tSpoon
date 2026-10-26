import { ImageResponse } from "next/og";

// iOS 홈 화면 아이콘 — 티스푼 마크(app/icon.svg)를 PNG 로. 빌드 때 한 번 만들어진다
export const size = { width: 180, height: 180 };
export const contentType = "image/png";

export default function AppleIcon() {
  return new ImageResponse(
    <div style={{ width: "100%", height: "100%", display: "flex", background: "#009944" }}>
      <svg viewBox="0 0 64 64" width="180" height="180">
        <g transform="rotate(-38 32 32)">
          <ellipse cx="32" cy="21" rx="10.5" ry="13" fill="#fff" />
          <rect x="29.25" y="31" width="5.5" height="25" rx="2.75" fill="#fff" />
          <circle cx="32" cy="22.5" r="5" fill="#ffc80a" />
        </g>
      </svg>
    </div>,
    size,
  );
}
