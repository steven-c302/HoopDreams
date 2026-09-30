// controller/src/tv/turf3d/ui/panels/TeamUpPanel.tsx
import type { TurfTv } from '../../../types'
import { Plate } from '../Card'
import { inkOn, type Person } from '../copy'
import { FaceIcon } from '../FaceIcon'
import { Label } from '../Label'
import { Pill } from '../Pill'
import { INK, MUTED, PAPER_DARK } from '../theme'

/** The teams: one row each, a colour plate with the team name, then its members' faces and first names. */
export function TeamUpPanel({ tv, people }: { tv: TurfTv; people: Person[] }) {
  const teams = tv.tokens.slice(0, 6)
  const top = tv.notice ? 96 : 118
  return (
    <group>
      <Label px={60} kind="title" y={200}>TEAMS!</Label>
      {tv.notice && <Label px={28} kind="body" y={158} color={MUTED} font="bodyBold" maxWidth={740}>{tv.notice}</Label>}
      {teams.map((t, i) => {
        const y = top - i * 64
        const members = t.members.map((id) => people.find((p) => p.id === id)).filter((p): p is Person => !!p)
        const shown = members.slice(0, 4)
        return (
          <group key={i}>
            <Plate w={182} h={50} r={12} color={INK} x={-290} y={y} z={1} />
            <Plate w={176} h={44} r={9} color={t.color} x={-290} y={y} z={2} />
            <Label px={22} kind="label" font="body" color={inkOn(t.color)} x={-290} y={y} z={3} maxWidth={176}>{t.name}</Label>
            {shown.map((p, k) => (
              <group key={p.id} position={[-184 + k * 132, y, 0]}>
                <FaceIcon face={p.face} color={p.color} size={42} x={14} />
                <Label px={22} kind="label" anchorX="left" align="left" x={40} maxWidth={92}>{p.name.split(' ')[0]}</Label>
              </group>
            ))}
            {members.length > 4 && <Pill w={64} h={34} text={`+${members.length - 4}`} fill={PAPER_DARK} x={372} y={y} />}
          </group>
        )
      })}
      <Label px={28} kind="body" y={-206} color={MUTED} font="bodyBold" maxWidth={740}>Shuffle: S on the TV or the captain's phone</Label>
    </group>
  )
}
