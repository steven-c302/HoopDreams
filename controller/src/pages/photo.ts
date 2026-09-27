/** Keep under MAX_BYTES in PhotoStore.kt. */
const MAX_BYTES = 96 * 1024

/**
 * Turns a camera shot or library photo into a face: the centre square, at most [edge] px, as a JPEG small enough for
 * the party server. Browsers apply the photo's EXIF rotation when they decode it, so selfies come out upright.
 */
export async function squareJpeg(file: Blob, edge = 256): Promise<Blob> {
  const url = URL.createObjectURL(file)
  try {
    const img = new Image()
    img.src = url
    await img.decode()
    const side = Math.min(img.naturalWidth, img.naturalHeight)
    if (!side) throw new Error('empty image')
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = Math.min(edge, side)
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('no canvas')
    ctx.drawImage(img, (img.naturalWidth - side) / 2, (img.naturalHeight - side) / 2, side, side, 0, 0, canvas.width, canvas.height)
    for (const quality of [0.85, 0.7, 0.55]) {
      const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
      if (blob && blob.size <= MAX_BYTES) return blob
    }
    throw new Error('photo too big')
  } finally {
    URL.revokeObjectURL(url)
  }
}

/** Uploads a face photo and returns its id; the player then joins with face `i:<id>`. */
export async function uploadPhoto(jpeg: Blob): Promise<string> {
  const r = await fetch('/api/avatar', { method: 'POST', headers: { 'Content-Type': 'image/jpeg' }, body: jpeg })
  if (!r.ok) throw new Error(`upload failed (${r.status})`)
  const { id } = (await r.json()) as { id?: string }
  if (!id || !/^[0-9a-f]{16}$/.test(id)) throw new Error('bad upload reply')
  return id
}
