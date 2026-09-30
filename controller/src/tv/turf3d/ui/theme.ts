// controller/src/tv/turf3d/ui/theme.ts
import antonUrl from './fonts/anton.woff?url'
import zilla500Url from './fonts/zilla-500.woff?url'
import zilla700Url from './fonts/zilla-700.woff?url'
import dsegUrl from './fonts/dseg7.woff?url'

export const INK = '#1a1a1a'
export const PAPER = '#fbf3dc'
export const PAPER_DARK = '#efe2bd'
export const RED = '#e2483d'
export const GOLD = '#ffd23f'
export const GREEN = '#2fbf55'
export const MUTED = '#6b5a3e'
export const SHADOW = '#2a1a0e'
export const LED_BG = '#241710'

/** Bundled fonts (troika reads .woff, not .woff2). Every Label passes one explicitly so nothing is fetched from a CDN. */
export const FONT = { display: antonUrl, body: zilla500Url, bodyBold: zilla700Url, led: dsegUrl }
export type FontName = keyof typeof FONT
