declare module '@letele/playing-cards' {
  import type { ComponentType, SVGProps } from 'react'
  const cards: Record<string, ComponentType<SVGProps<SVGSVGElement>>>
  export = cards
}
