import gsap from 'gsap'
import { MotionPathPlugin } from 'gsap/MotionPathPlugin'
import { useLayoutEffect, useRef } from 'react'
import type { PlayingCard } from '../protocol'
import * as deck from '@letele/playing-cards'
import type { ComponentType, SVGProps } from 'react'
import './card.css'

gsap.registerPlugin(MotionPathPlugin)

const RANKS = ['', 'A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K']

const SUIT_LETTER = ['S', 'H', 'D', 'C']
const RANK_KEY = ['', 'a', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'j', 'q', 'k']
type SvgCard = ComponentType<SVGProps<SVGSVGElement>>

/** Card faces are Adrian Kennard's classic deck (Goodall & Son court figures), CC0, via @letele/playing-cards. */
function Face({ card }: { card: PlayingCard }) {
  const Svg = (deck as unknown as Record<string, SvgCard>)[SUIT_LETTER[card.suit] + RANK_KEY[card.rank]]
  return (
    <div className="card-svg" role="img" aria-label={`${RANKS[card.rank]} of ${['spades', 'hearts', 'diamonds', 'clubs'][card.suit]}`}>
      <Svg className="card-svg" preserveAspectRatio="none" />
    </div>
  )
}

function Back() {
  return (
    <svg viewBox="0 0 100 140" className="card-svg" aria-label="face-down card">
      <rect x="1" y="1" width="98" height="138" rx="8" fill="var(--tomato)" stroke="var(--ink)" strokeWidth="2.4" />
      <rect x="7" y="7" width="86" height="126" rx="5" fill="url(#card-lattice)" stroke="var(--ink)" strokeWidth="1.6" />
      <circle cx="50" cy="70" r="18" fill="var(--sun)" stroke="var(--ink)" strokeWidth="2.4" />
      <text x="50" y="79" textAnchor="middle" fontSize="24" fill="var(--ink)" fontFamily="Rammetto One">P</text>
    </svg>
  )
}

interface Props {
  card: PlayingCard
  width?: number
  /** Where the card is dealt from, relative to where it lands. Omit for no deal animation. */
  from?: { x: number; y: number }
  delay?: number
  tilt?: number
  animate?: boolean
  /** Called when the card lands (sound hooks). */
  onLand?: () => void
}

/**
 * A playing card. Dealt cards fly face-down from the shoe along an arc, land, then lift off the felt and flip
 * face-up with a glare sweep. A face-down card that becomes known later (the dealer's hole card) flips the same way.
 */
export function Card({ card, width = 96, from, delay = 0, tilt = 0, animate = true, onLand }: Props) {
  const flyer = useRef<HTMLDivElement>(null)
  const inner = useRef<HTMLDivElement>(null)
  const glare = useRef<HTMLDivElement>(null)
  const shownUp = useRef(!animate || !from ? card.rank > 0 : false)
  const up = card.rank > 0

  const flip = (tl: gsap.core.Timeline) => {
    tl.to(inner.current, {
      keyframes: [
        { rotateY: 90, z: width * 0.9, scale: 1.14, duration: 0.2, ease: 'power2.in' },
        { rotateY: 0, z: 0, scale: 1, duration: 0.3, ease: 'back.out(1.6)' },
      ],
    })
    tl.fromTo(glare.current, { xPercent: -140, opacity: 1 }, { xPercent: 140, opacity: 0.2, duration: 0.45, ease: 'power2.out' }, '-=0.12')
    shownUp.current = true
  }

  // Deal on mount.
  useLayoutEffect(() => {
    const el = flyer.current, inn = inner.current
    if (!el || !inn) return
    const ctx = gsap.context(() => {
      if (!animate || !from) { gsap.set(inn, { rotateY: up ? 0 : 180 }); return }
      gsap.set(inn, { rotateY: 180 })
      const tl = gsap.timeline({ delay })
      const mid = { x: from.x * 0.45, y: Math.min(from.y, 0) * 0.45 - width * 1.3 }
      tl.fromTo(el,
        { x: from.x, y: from.y, rotation: tilt - 200, scale: 0.72, opacity: 0 },
        { motionPath: { path: [{ x: from.x, y: from.y }, mid, { x: 0, y: 0 }], curviness: 1.1 }, rotation: tilt, scale: 1, opacity: 1,
          duration: 0.62, ease: 'power3.out', immediateRender: true })
      tl.to(el, { y: -3, duration: 0.07, yoyo: true, repeat: 1, ease: 'sine.out' })
      tl.call(() => onLand?.())
      if (up) flip(tl)
    })
    return () => ctx.revert()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // A face-down card that becomes known flips over.
  useLayoutEffect(() => {
    if (!up || shownUp.current || !inner.current) return
    if (animate && from && !gsap.isTweening(flyer.current)) {
      const tl = gsap.timeline()
      flip(tl)
      return () => { tl.kill() }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [up])

  return (
    <div className="card" style={{ width, height: width * 1.4 }}>
      <div ref={flyer} className="card-flyer" style={animate && from ? undefined : { transform: `rotate(${tilt}deg)` }}>
        <div ref={inner} className="card-inner">
          <div className="card-face">{up ? <Face card={card} /> : <Back />}<div ref={glare} className="card-glare" /></div>
          <div className="card-face card-back"><Back /></div>
        </div>
      </div>
    </div>
  )
}
