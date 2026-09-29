type Segment = "a" | "b" | "c" | "d" | "e" | "f" | "g";

// x, y, width, height inside a 12x20 cell: a=top, b/c=right, d=bottom, e/f=left, g=middle
const RECTS: Record<Segment, [number, number, number, number]> = {
  a: [2, 0, 8, 2],
  b: [10, 2, 2, 7],
  c: [10, 11, 2, 7],
  d: [2, 18, 8, 2],
  e: [0, 11, 2, 7],
  f: [0, 2, 2, 7],
  g: [2, 9, 8, 2],
};
const SEGMENTS = Object.keys(RECTS) as Segment[];
const LIT: Record<string, string> = {
  "0": "abcdef", "1": "bc", "2": "abdeg", "3": "abcdg", "4": "bcfg", "5": "acdfg",
  "6": "acdefg", "7": "abc", "8": "abcdefg", "9": "abcdfg", "-": "g", " ": "",
};

interface LedDigitsProps {
  value: string | number;
  digits?: number;
  size?: string;
  color?: string;
}

/** Seven-segment scoreboard digits. Supports 0-9, "-", ":" and spaces. */
export function LedDigits({ value, digits, size = "1.5em", color = "var(--led)" }: LedDigitsProps) {
  const raw = String(value);
  const text = digits ? raw.padStart(digits, " ").slice(-digits) : raw;
  let x = 0;
  const cells = [...text].map((ch, i) => {
    const left = x;
    x += ch === ":" ? 6 : 15;
    if (ch === ":") {
      return (
        <g key={i} fill={color}>
          <rect x={left + 1} y={5} width={2.2} height={2.2} />
          <rect x={left + 1} y={12.8} width={2.2} height={2.2} />
        </g>
      );
    }
    const lit = LIT[ch] ?? "";
    return (
      <g key={i} transform={`translate(${left} 0)`}>
        {SEGMENTS.map((seg) => {
          const [rx, ry, w, h] = RECTS[seg];
          const on = lit.includes(seg);
          return <rect key={seg} x={rx} y={ry} width={w} height={h} rx={0.9} fill={on ? color : "var(--led-dim)"} opacity={on ? 1 : 0.5} />;
        })}
      </g>
    );
  });
  const width = Math.max(x - 3, 1);
  return (
    <svg className="led" viewBox={`0 0 ${width} 20`} style={{ height: size, aspectRatio: `${width} / 20` }} role="img" aria-label={raw}>
      {cells}
    </svg>
  );
}
