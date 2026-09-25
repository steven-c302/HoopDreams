export interface UndoToast {
  requestId: string;
  label: string;
  until: number;
}

export interface NotMeToast {
  shotId: string;
  by: string;
}

interface ToastsProps {
  undo: UndoToast | null;
  onUndo: () => void;
  notMe: NotMeToast | null;
  onNotMe: (shotId: string) => void;
  onFair: (shotId: string) => void;
  error: string | null;
}

export function Toasts({ undo, onUndo, notMe, onNotMe, onFair, error }: ToastsProps) {
  return (
    <div className="toast-stack">
      {error && (
        <div className="toast toast--error" role="alert">
          <span className="toast__text">{error}</span>
        </div>
      )}
      {notMe && (
        <div className="toast">
          <span className="toast__text">
            <b>{notMe.by}</b> logged a shot for you
          </span>
          <button className="btn btn--danger" onClick={() => onNotMe(notMe.shotId)}>
            NOT ME
          </button>
          <button className="btn btn--ghost" aria-label="Fair" onClick={() => onFair(notMe.shotId)}>
            👍
          </button>
        </div>
      )}
      {undo && (
        <div className="toast">
          <span className="toast__text">+1 for {undo.label}</span>
          <button className="btn btn--ghost" onClick={onUndo}>
            UNDO
          </button>
        </div>
      )}
    </div>
  );
}
