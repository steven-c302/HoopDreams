import { useRef, useState, type PointerEvent } from "react";
import type { Stroke } from "./state";
export function Drawing({
  strokes,
  onStroke,
  disabled = false,
}: {
  strokes: Stroke[];
  onStroke?: (stroke: Stroke) => void;
  disabled?: boolean;
}) {
  const [color, setColor] = useState("#242331");
  const [pending, setPending] = useState<[number, number][]>([]);
  const points = useRef<[number, number][]>([]);
  const active = useRef<number | null>(null);
  const position = (e: PointerEvent<SVGSVGElement>): [number, number] => {
    const rect = e.currentTarget.getBoundingClientRect();
    return [
      Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width)),
      Math.max(0, Math.min(1, (e.clientY - rect.top) / rect.height)),
    ];
  };
  function flush(keep = false) {
    if (points.current.length)
      onStroke?.({ points: [...points.current], color });
    points.current = keep ? points.current.slice(-1) : [];
    setPending([...points.current]);
  }
  return (
    <div className="gn-drawing">
      <svg
        viewBox="0 0 1000 600"
        role="img"
        aria-label={
          onStroke ? "Draw here with your finger" : "Live drawing canvas"
        }
        style={{ touchAction: onStroke ? "none" : "auto" }}
        onPointerDown={(e) => {
          if (!onStroke || disabled || active.current !== null) return;
          active.current = e.pointerId;
          e.currentTarget.setPointerCapture(e.pointerId);
          points.current = [position(e)];
          setPending([...points.current]);
        }}
        onPointerMove={(e) => {
          if (active.current !== e.pointerId || disabled) return;
          points.current.push(position(e));
          setPending([...points.current]);
          if (points.current.length >= 12) flush(true);
        }}
        onPointerUp={(e) => {
          if (active.current !== e.pointerId) return;
          flush();
          active.current = null;
        }}
        onPointerCancel={() => {
          flush();
          active.current = null;
        }}
      >
        <rect width="1000" height="600" fill="#fffdf7" />
        {[...strokes, { points: pending, color }]
          .filter((s) => s.points.length)
          .map((s, i) =>
            s.points.length === 1 ? (
              <circle
                key={i}
                cx={s.points[0][0] * 1000}
                cy={s.points[0][1] * 600}
                r="4"
                fill={s.color}
              />
            ) : (
              <polyline
                key={i}
                points={s.points
                  .map(([x, y]) => `${x * 1000},${y * 600}`)
                  .join(" ")}
                fill="none"
                stroke={s.color}
                strokeWidth="7"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            ),
          )}
        {!strokes.length && !pending.length && (
          <text
            x="500"
            y="300"
            textAnchor="middle"
            fill="#b4afbf"
            fontSize="26"
          >
            {onStroke
              ? "Your masterpiece starts here"
              : "Waiting for the first stroke…"}
          </text>
        )}
      </svg>
      {onStroke && (
        <div className="gn-palette">
          {["#242331", "#f06a4f", "#7758d9", "#269f85"].map((c, i) => (
            <button
              key={c}
              aria-label={
                ["Black ink", "Orange ink", "Purple ink", "Green ink"][i]
              }
              aria-pressed={color === c}
              style={{ background: c }}
              onClick={() => setColor(c)}
            />
          ))}
        </div>
      )}
    </div>
  );
}
