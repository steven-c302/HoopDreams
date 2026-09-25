export interface UploadResult {
  ok: boolean;
  mediaId?: string;
  error?: string;
}

interface UploadFields {
  token: string;
  purpose: "shot" | "avatar";
  shotId?: string | null;
  filename: string;
}

/** POST a photo or clip with progress reporting (fetch can't report upload progress). */
export function uploadMedia(file: Blob, fields: UploadFields, onProgress: (fraction: number) => void): Promise<UploadResult> {
  return new Promise((resolve) => {
    const form = new FormData();
    form.append("token", fields.token);
    form.append("purpose", fields.purpose);
    if (fields.shotId) form.append("shotId", fields.shotId);
    form.append("file", file, fields.filename);
    const xhr = new XMLHttpRequest();
    xhr.open("POST", "/api/party/media");
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress(e.loaded / e.total);
    };
    xhr.onload = () => {
      if (xhr.status === 200) {
        resolve({ ok: true, mediaId: (JSON.parse(xhr.responseText) as { mediaId: string }).mediaId });
        return;
      }
      let error = `Upload failed (${xhr.status})`;
      try {
        error = (JSON.parse(xhr.responseText) as { detail?: string }).detail ?? error;
      } catch {
        // not JSON: keep the generic message
      }
      resolve({ ok: false, error });
    };
    xhr.onerror = () => resolve({ ok: false, error: "Upload failed. Check the Wi-Fi." });
    xhr.send(form);
  });
}
