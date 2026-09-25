interface HatProps {
  onDraw: () => void;
  isDrawing: boolean;
  disabled: boolean;
}

export function Hat({ onDraw, isDrawing, disabled }: HatProps) {
  return (
    <button
      className={`hat-button ${isDrawing ? "hat-button--shaking" : ""}`}
      onClick={onDraw}
      disabled={disabled || isDrawing}
      aria-label="Draw a game from the hat"
    >
      <svg
        viewBox="0 0 220 200"
        className="hat-svg"
        xmlns="http://www.w3.org/2000/svg"
      >
        <ellipse cx="110" cy="176" rx="98" ry="16" fill="#DCEFFA" />
        <rect x="18" y="150" width="184" height="26" rx="13" fill="var(--blue)" stroke="var(--blue-dark)" strokeWidth="3" />
        <rect x="60" y="46" width="100" height="112" rx="14" fill="var(--cream)" stroke="var(--blue-dark)" strokeWidth="3" />
        <rect x="60" y="118" width="100" height="24" fill="var(--blue)" stroke="var(--blue-dark)" strokeWidth="3" />
        <ellipse cx="110" cy="46" rx="50" ry="14" fill="var(--cream)" stroke="var(--blue-dark)" strokeWidth="3" />
        <text x="98" y="90" fontSize="22" className="hat-sparkle">✨</text>
        <text x="128" y="112" fontSize="16" className="hat-sparkle hat-sparkle--delay">🎉</text>
      </svg>
      <span className="hat-cta">{isDrawing ? "Drawing…" : "Tap the hat!"}</span>
    </button>
  );
}
