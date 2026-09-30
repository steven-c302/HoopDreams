// controller/src/tv/turf3d/ui/CountText.tsx
import { Text } from '@react-three/drei'
import { useFrame } from '@react-three/fiber'
import { useEffect, useRef } from 'react'
import { money } from './copy'
import { countAt } from './rails'
import { textPx } from './sizing'
import { FONT, INK } from './theme'

interface TroikaText { text: string; sync: () => void }

/** A dollar amount in Anton that counts to a new value over 700 ms and always ends exactly on it. */
export function CountText({ value, px, color = INK, x = 0, y = 0, z = 3, anchorX = 'left' }: { value: number; px: number; color?: string; x?: number; y?: number; z?: number; anchorX?: 'left' | 'center' | 'right' }) {
  const text = useRef<TroikaText | null>(null)
  const shown = useRef(value)
  const from = useRef(value)
  const target = useRef(value)
  const t0 = useRef(0)
  useEffect(() => {
    if (value === target.current) return
    from.current = shown.current
    target.current = value
    t0.current = performance.now()
  }, [value])
  useFrame(() => {
    if (shown.current === target.current) return
    const n = countAt(from.current, target.current, (performance.now() - t0.current) / 700)
    if (n !== shown.current && text.current) { shown.current = n; text.current.text = money(n); text.current.sync() }
    else if (n === shown.current && performance.now() - t0.current > 700) shown.current = target.current
  })
  return (
    <Text ref={text as never} font={FONT.display} fontSize={textPx('title', px)} color={color} anchorX={anchorX} anchorY="middle" position={[x, y, z]}>{money(value)}</Text>
  )
}
