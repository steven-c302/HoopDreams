// controller/src/tv/turf3d/drinkSpecs.ts
/** Home Turf's pieces as drinks. Pure data so it can be tested without WebGL. Labels are generic on purpose: no brands. */
export const DRINK_KINDS = ['soju', 'vodka', 'beer', 'can', 'shot', 'cup'] as const
export type DrinkKind = (typeof DRINK_KINDS)[number]
export type BottleKind = 'soju' | 'vodka' | 'beer'

export interface BottleLabel { y0: number; y1: number; bg: string; accent: string; text: string; sub: string; ink: string }
export interface BottleCfg {
  r: number; body: number; neck: number; shoulder: number; top: number
  glass: string; att: string
  label: BottleLabel
  cap: { color: string; metal: boolean }
}

export const BOTTLES: Record<BottleKind, BottleCfg> = {
  soju: { r: 0.27, body: 0.5, neck: 0.085, shoulder: 0.74, top: 0.94, glass: '#7fe0a4', att: '#3aa866',
    label: { y0: 0.1, y1: 0.4, bg: '#f5f5ec', accent: '#2d9a55', text: 'SOJU', sub: 'ORIGINAL', ink: '#1b6b3a' }, cap: { color: '#2d9a55', metal: false } },
  vodka: { r: 0.25, body: 0.56, neck: 0.09, shoulder: 0.84, top: 1.02, glass: '#eef7ff', att: '#d4e8f5',
    label: { y0: 0.14, y1: 0.52, bg: '#c62828', accent: '#ffffff', text: 'VODKA', sub: 'PREMIUM', ink: '#ffffff' }, cap: { color: '#d7d7de', metal: true } },
  beer: { r: 0.2, body: 0.4, neck: 0.07, shoulder: 0.72, top: 0.96, glass: '#e08a2a', att: '#c46a10',
    label: { y0: 0.1, y1: 0.34, bg: '#f3e2b8', accent: '#b8341f', text: 'BEER', sub: 'COLD LAGER', ink: '#7a1f12' }, cap: { color: '#c9a227', metal: true } },
}

export const CAN_LABEL = { top: 'LAGER', sub: 'COLD & CRISP' }

export const DRINK_NAMES: Record<DrinkKind, string> = {
  soju: 'Soju Bottle', vodka: 'Vodka Bottle', beer: 'Beer Bottle', can: 'Beer Can', shot: 'Shot Glass', cup: 'Red Cup',
}

/** Every string printed on a drink or used as its name, for the brand deny-list test. */
export function labelStrings(): string[] {
  const out: string[] = []
  for (const b of Object.values(BOTTLES)) out.push(b.label.text, b.label.sub)
  out.push(CAN_LABEL.top, CAN_LABEL.sub, ...Object.values(DRINK_NAMES))
  return out
}
