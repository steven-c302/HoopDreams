/**
 * Shrink a photo on the phone before upload (a 12 MP HEIC becomes a ~300 KB JPEG).
 * If the browser can't decode it, the original goes up and the server's ffmpeg normalises it.
 */
export async function downscaleImage(file: File, maxEdge = 1600, square = false): Promise<Blob> {
  try {
    const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
    const side = Math.min(bitmap.width, bitmap.height);
    const sw = square ? side : bitmap.width;
    const sh = square ? side : bitmap.height;
    const sx = square ? (bitmap.width - side) / 2 : 0;
    const sy = square ? (bitmap.height - side) / 2 : 0;
    const scale = Math.min(1, maxEdge / Math.max(sw, sh));
    const canvas = document.createElement("canvas");
    canvas.width = Math.round(sw * scale);
    canvas.height = Math.round(sh * scale);
    canvas.getContext("2d")!.drawImage(bitmap, sx, sy, sw, sh, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    return await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("encode failed"))), "image/jpeg", 0.85),
    );
  } catch {
    return file;
  }
}
