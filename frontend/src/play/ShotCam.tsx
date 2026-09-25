import { useRef, useState } from "react";
import { downscaleImage } from "./image";
import { uploadMedia } from "./upload";

type Status =
  | { phase: "idle" }
  | { phase: "uploading"; progress: number }
  | { phase: "done" }
  | { phase: "error"; message: string; file: File };

interface ShotCamProps {
  token: string;
  /** Attach to the shot this phone just logged or was logged for, if any. */
  recentShotId: string | null;
}

export function ShotCam({ token, recentShotId }: ShotCamProps) {
  const input = useRef<HTMLInputElement>(null);
  const [status, setStatus] = useState<Status>({ phase: "idle" });

  async function send(file: File) {
    const isVideo = file.type.startsWith("video/");
    const blob = isVideo ? file : await downscaleImage(file);
    const filename = blob === file ? file.name || (isVideo ? "clip.mp4" : "photo.jpg") : "photo.jpg";
    setStatus({ phase: "uploading", progress: 0 });
    const result = await uploadMedia(blob, { token, purpose: "shot", shotId: recentShotId, filename }, (progress) =>
      setStatus({ phase: "uploading", progress }),
    );
    if (result.ok) {
      setStatus({ phase: "done" });
      setTimeout(() => setStatus({ phase: "idle" }), 3000);
    } else {
      setStatus({ phase: "error", message: result.error ?? "Upload failed", file });
    }
  }

  return (
    <div className="shotcam">
      <input
        ref={input}
        type="file"
        accept="image/*,video/*"
        hidden
        data-testid="shotcam-input"
        onChange={(e) => {
          const file = e.target.files?.[0];
          e.target.value = "";
          if (file) void send(file);
        }}
      />
      {status.phase === "idle" && (
        <button className="btn btn--ghost shotcam__button" onClick={() => input.current?.click()}>
          📸 SHOT-CAM <span className="shotcam__hint">photo or clip → TV replay</span>
        </button>
      )}
      {status.phase === "uploading" && (
        <div className="shotcam__progress" aria-live="polite">
          <span className="shotcam__bar" style={{ width: `${Math.round(status.progress * 100)}%` }} />
          <p>Sending to the TV… {Math.round(status.progress * 100)}%</p>
        </div>
      )}
      {status.phase === "done" && <p className="shotcam__done">✅ On its way to the jumbotron!</p>}
      {status.phase === "error" && (
        <div className="shotcam__error" role="alert">
          <span>{status.message}</span>
          <button className="btn btn--small" onClick={() => void send(status.file)}>
            RETRY
          </button>
          <button className="btn btn--ghost btn--small" aria-label="Dismiss" onClick={() => setStatus({ phase: "idle" })}>
            ✕
          </button>
        </div>
      )}
    </div>
  );
}
