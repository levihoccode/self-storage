import type { CSSProperties } from "react";
import type { UnitType } from "../../app/catalog";

type Face = {
  key: string;
  width: number;
  height: number;
  left: number;
  top: number;
  transform: string;
  background: string;
  door?: boolean;
};

const DOOR = "repeating-linear-gradient(0deg, var(--lp-door-a) 0 7px, var(--lp-door-b) 7px 8px)";

/**
 * Hộp 3D bằng CSS theo đúng kích thước thật (mét × scale px/m): rộng (X), cao (Y), sâu (Z).
 * Đây là mô hình minh hoạ tỷ lệ, không phải ảnh chụp khoang.
 */
export function boxMetrics(unit: UnitType, scale: number) {
  const width = Math.round(unit.width * scale);
  const depth = Math.round(unit.depth * scale);
  const height = Math.round(unit.height * scale);
  const faces: Face[] = [
    {
      key: "front",
      width,
      height,
      left: 0,
      top: 0,
      transform: `translateZ(${depth / 2}px)`,
      background: DOOR,
      door: true,
    },
    {
      key: "back",
      width,
      height,
      left: 0,
      top: 0,
      transform: `rotateY(180deg) translateZ(${depth / 2}px)`,
      background: "var(--lp-wall)",
    },
    {
      key: "right",
      width: depth,
      height,
      left: (width - depth) / 2,
      top: 0,
      transform: `rotateY(90deg) translateZ(${width / 2}px)`,
      background: "var(--lp-wall)",
    },
    {
      key: "left",
      width: depth,
      height,
      left: (width - depth) / 2,
      top: 0,
      transform: `rotateY(-90deg) translateZ(${width / 2}px)`,
      background: "var(--lp-wall)",
    },
    {
      key: "top",
      width,
      height: depth,
      left: 0,
      top: (height - depth) / 2,
      transform: `rotateX(90deg) translateZ(${height / 2}px)`,
      background: "var(--lp-roof)",
    },
    {
      key: "bottom",
      width,
      height: depth,
      left: 0,
      top: (height - depth) / 2,
      transform: `rotateX(-90deg) translateZ(${height / 2}px)`,
      background: "var(--lp-floor)",
    },
  ];
  return { width, depth, height, faces };
}

export function UnitBox3D({
  unit,
  scale,
  angle,
  tilt = -20,
}: {
  unit: UnitType;
  scale: number;
  angle: number;
  tilt?: number;
}) {
  const box = boxMetrics(unit, scale);
  const root: CSSProperties = {
    width: box.width,
    height: box.height,
    position: "relative",
    transformStyle: "preserve-3d",
    transform: `rotateX(${tilt}deg) rotateY(${angle}deg)`,
  };
  return (
    <div style={root} aria-hidden="true">
      {box.faces.map((face) => (
        <div
          key={face.key}
          style={{
            position: "absolute",
            boxSizing: "border-box",
            width: face.width,
            height: face.height,
            left: face.left,
            top: face.top,
            transform: face.transform,
            background: face.background,
            border: "1.5px solid var(--lp-teal)",
            backfaceVisibility: "hidden",
          }}
        >
          {face.door && (
            <div
              style={{
                position: "absolute",
                right: "12%",
                top: "52%",
                width: "9%",
                minWidth: 6,
                height: "6%",
                minHeight: 5,
                background: "var(--lp-teal)",
                borderRadius: 2,
              }}
            />
          )}
        </div>
      ))}
    </div>
  );
}
