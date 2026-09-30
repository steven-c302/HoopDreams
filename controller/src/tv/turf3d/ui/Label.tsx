// controller/src/tv/turf3d/ui/Label.tsx
import { Text } from '@react-three/drei'
import { FONT, INK, type FontName } from './theme'
import { textPx, type TextKind } from './sizing'

/** Hero and title text is Anton; body and label text is Zilla Slab, unless [font] says otherwise. */
const DEFAULT_FONT: Record<TextKind, FontName> = { hero: 'display', title: 'display', body: 'body', label: 'body' }

export interface LabelProps {
  children: string
  px: number
  kind?: TextKind
  color?: string
  x?: number; y?: number; z?: number
  maxWidth?: number
  align?: 'left' | 'center' | 'right'
  anchorX?: 'left' | 'center' | 'right'
  font?: FontName
  /** An ink outline that makes light text read like a sticker. */
  outline?: string
  onSync?: () => void
}

/** SDF text in reference pixels. The size is clamped up to the minimum for its kind, so nothing here can be too small to read. */
export function Label({ children, px, kind = 'body', color = INK, x = 0, y = 0, z = 1, maxWidth, align = 'center', anchorX = 'center', font, outline, onSync }: LabelProps) {
  const size = textPx(kind, px)
  return (
    <Text
      font={FONT[font ?? DEFAULT_FONT[kind]]}
      fontSize={size}
      color={color}
      maxWidth={maxWidth}
      textAlign={align}
      anchorX={anchorX}
      anchorY="middle"
      lineHeight={1.05}
      position={[x, y, z]}
      outlineWidth={outline ? size * 0.07 : 0}
      outlineColor={outline ?? INK}
      onSync={onSync}
    >
      {children}
    </Text>
  )
}
