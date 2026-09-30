// controller/src/tv/turf3d/ui/DrinkIcon.tsx
import { Drink } from '../Drinks'
import { drinkFor } from '../pieces'

/**
 * One of the six drinks, standing on its coaster, tilted to be seen from above like the ones on the board. [size] is its
 * height in reference pixels (a drink is about one unit tall). Lit by the Dais's own light, so it looks the same in every shot.
 */
export function DrinkIcon({ piece, color, size = 70, x = 0, y = 0 }: { piece?: string; color: string; size?: number; x?: number; y?: number }) {
  return (
    <group position={[x, y, 30]} scale={size} rotation-x={0.75}>
      <Drink kind={drinkFor(piece)} color={color} />
    </group>
  )
}
