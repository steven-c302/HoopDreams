import { useEffect, useRef, useState } from "react";
import { fetchTriviaQuestion } from "../api";
import type { TriviaAnswer, TriviaQuestion } from "../types";

interface TriviaGameProps {
  onExit: () => void;
}

const DIFFICULTY_LABEL: Record<TriviaQuestion["difficulty"], string> = {
  easy: "Easy 🌤️",
  medium: "Medium 🔥",
  hard: "Hard 💀",
};

export function TriviaGame({ onExit }: TriviaGameProps) {
  const [question, setQuestion] = useState<TriviaQuestion | null>(null);
  const [selected, setSelected] = useState<TriviaAnswer | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const hasMounted = useRef(false);

  function loadQuestion() {
    setLoading(true);
    setError(null);
    setSelected(null);
    fetchTriviaQuestion()
      .then(setQuestion)
      .catch(() => setError("Couldn't load a question — the trivia source may be busy, try again."))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    if (hasMounted.current) return;
    hasMounted.current = true;
    loadQuestion();
  }, []);

  return (
    <div className="trivia">
      <button className="trivia__exit" onClick={onExit}>
        ← Back to the hat
      </button>

      {loading && <p className="trivia__status">Shuffling a question…</p>}

      {error && (
        <div className="trivia__error">
          <p>{error}</p>
          <button className="reset-button" onClick={loadQuestion}>
            Try again
          </button>
        </div>
      )}

      {!loading && !error && question && (
        <div className="trivia-card">
          <div className="trivia-card__meta">
            <span className={`trivia-card__difficulty trivia-card__difficulty--${question.difficulty}`}>
              {DIFFICULTY_LABEL[question.difficulty]}
            </span>
            <span className="trivia-card__category">{question.category}</span>
          </div>

          <h2 className="trivia-card__question">{question.question}</h2>

          <div className="trivia-card__answers">
            {question.answers.map((answer) => {
              const isSelected = selected?.text === answer.text;
              const showState = selected !== null;
              const className = [
                "trivia-answer",
                showState && answer.correct ? "trivia-answer--correct" : "",
                showState && isSelected && !answer.correct ? "trivia-answer--wrong" : "",
              ]
                .filter(Boolean)
                .join(" ");

              return (
                <button
                  key={answer.text}
                  className={className}
                  disabled={showState}
                  onClick={() => setSelected(answer)}
                >
                  {answer.text}
                </button>
              );
            })}
          </div>

          {selected && (
            <button className="trivia-card__next" onClick={loadQuestion}>
              Next question →
            </button>
          )}
        </div>
      )}
    </div>
  );
}
