"use client";

import clsx from "clsx";
import {
  useEffect,
  useId,
  useRef,
  useState,
  useSyncExternalStore,
  type ComponentPropsWithoutRef,
  type CSSProperties,
  type ElementType,
} from "react";

/*
 * Liquid Glass 의 렌즈 굴절.
 * 요소 크기에 맞춰 둥근 사각형 가장자리(bezel)에서만 안쪽으로 빛이 휘는 변위 맵을 그리고,
 * backdrop-filter 에 SVG feDisplacementMap 을 건다.
 * backdrop-filter 의 url() 은 Chromium 만 지원하므로 그 밖의 브라우저는 .glass 의 블러·림만 보인다.
 */

function detectRefraction() {
  if (typeof window === "undefined") return false;
  const brands = (navigator as Navigator & { userAgentData?: { brands: { brand: string }[] } }).userAgentData?.brands;
  const chromium = Boolean(brands?.some((b) => /Chromium/i.test(b.brand)));
  const reduced = window.matchMedia("(prefers-reduced-transparency: reduce)").matches;
  return chromium && !reduced;
}

let cachedSupport: boolean | null = null;
const noopSubscribe = () => () => {};

function useRefractionSupport() {
  return useSyncExternalStore(
    noopSubscribe,
    () => (cachedSupport ??= detectRefraction()),
    () => false,
  );
}

const mapCache = new Map<string, string>();

function displacementMap(width: number, height: number, radius: number, bezel: number) {
  const key = `${width}x${height}r${radius}b${bezel}`;
  const hit = mapCache.get(key);
  if (hit) return hit;

  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d");
  if (!ctx) return "";
  const image = ctx.createImageData(width, height);
  const data = image.data;
  const hw = width / 2;
  const hh = height / 2;
  const r = Math.min(radius, hw, hh);

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const px = x + 0.5 - hw;
      const py = y + 0.5 - hh;
      const qx = Math.abs(px) - (hw - r);
      const qy = Math.abs(py) - (hh - r);
      // 둥근 사각형의 부호 있는 거리(안쪽이 음수) → 가장자리에서 안쪽으로 들어온 거리
      const inside = -(Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r);

      let nx = 0;
      let ny = 0;
      let strength = 0;
      if (inside > 0 && inside < bezel) {
        if (qx > 0 && qy > 0) {
          const len = Math.hypot(qx, qy) || 1;
          nx = (qx / len) * Math.sign(px);
          ny = (qy / len) * Math.sign(py);
        } else if (qx > qy) {
          nx = Math.sign(px);
        } else {
          ny = Math.sign(py);
        }
        // 볼록한 유리 단면: 가장자리로 갈수록 급하게 휜다
        const t = 1 - inside / bezel;
        strength = 1 - Math.sqrt(1 - t * t);
      }

      const i = (y * width + x) * 4;
      data[i] = 128 - nx * strength * 127;
      data[i + 1] = 128 - ny * strength * 127;
      data[i + 2] = 128;
      data[i + 3] = 255;
    }
  }
  ctx.putImageData(image, 0, 0);
  const url = canvas.toDataURL();
  mapCache.set(key, url);
  return url;
}

type Props<T extends ElementType> = {
  as?: T;
  /** border-radius(px). 알약 모양이면 높이의 절반 */
  radius: number;
  /** 굴절이 일어나는 가장자리 폭(px) */
  bezel?: number;
  /** 굴절 세기(px) */
  strength?: number;
  /** 흐림 정도(px) */
  blur?: number;
  thick?: boolean;
} & Omit<ComponentPropsWithoutRef<T>, "as">;

export function LiquidGlass<T extends ElementType = "div">({
  as,
  radius,
  bezel = 18,
  strength = 56,
  blur = 10,
  thick = false,
  className,
  style,
  children,
  ...rest
}: Props<T>) {
  const Tag = (as ?? "div") as ElementType;
  const ref = useRef<HTMLElement>(null);
  const supported = useRefractionSupport();
  const rawId = useId();
  const filterId = `lg${rawId.replace(/[^a-zA-Z0-9_-]/g, "")}`;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);

  useEffect(() => {
    const el = ref.current;
    if (!supported || !el) return;
    let frame = 0;
    const observer = new ResizeObserver(() => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const w = Math.round(el.offsetWidth);
        const h = Math.round(el.offsetHeight);
        if (w > 0 && h > 0) setSize((prev) => (prev && prev.w === w && prev.h === h ? prev : { w, h }));
      });
    });
    observer.observe(el);
    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
    };
  }, [supported]);

  const map = supported && size ? displacementMap(size.w, size.h, radius, Math.min(bezel, size.h / 2)) : null;
  const glassStyle: CSSProperties = {
    borderRadius: radius,
    ...(map
      ? {
          backdropFilter: `url(#${filterId}) blur(${blur}px) saturate(190%)`,
          WebkitBackdropFilter: `url(#${filterId}) blur(${blur}px) saturate(190%)`,
        }
      : {}),
    ...style,
  };

  return (
    <Tag ref={ref} className={clsx("glass", thick && "glass-thick", className)} style={glassStyle} {...rest}>
      {map && size && (
        <svg aria-hidden width="0" height="0" style={{ position: "absolute" }} focusable="false">
          <filter
            id={filterId}
            x="0"
            y="0"
            width={size.w}
            height={size.h}
            filterUnits="userSpaceOnUse"
            colorInterpolationFilters="sRGB"
          >
            <feImage href={map} x="0" y="0" width={size.w} height={size.h} preserveAspectRatio="none" result="map" />
            <feDisplacementMap in="SourceGraphic" in2="map" scale={strength} xChannelSelector="R" yChannelSelector="G" />
          </filter>
        </svg>
      )}
      {children}
    </Tag>
  );
}
