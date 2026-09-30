// controller/src/tv/turf3d/fonts.ts
import '@fontsource/anton'
import '@fontsource/rammetto-one'

/** The board texture is painted once, so wait until both faces are actually loaded. */
export function ensureFonts(): Promise<void> {
  return Promise.all([document.fonts.load('56px Anton'), document.fonts.load('180px "Rammetto One"')]).then(() => undefined, () => undefined)
}
