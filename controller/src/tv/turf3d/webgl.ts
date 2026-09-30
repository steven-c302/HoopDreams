// controller/src/tv/turf3d/webgl.ts
export function hasWebGL2(make: () => { getContext(id: string): unknown } = () => document.createElement('canvas')): boolean {
  try { return !!make().getContext('webgl2') } catch { return false }
}

/** 3D when the browser can do it and nobody asked for the flat board with ?board=2d. */
export function wants3d(search: string, supported: boolean): boolean {
  return supported && new URLSearchParams(search).get('board') !== '2d'
}
