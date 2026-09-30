// controller/src/tv/turf3d/scene/Lights.tsx
import { Environment, Lightformer } from '@react-three/drei'

/** One warm spotlight with soft shadows, plus local reflections (no downloaded HDRI, so it works offline). */
export function Lights() {
  return (
    <>
      <Environment resolution={256} environmentIntensity={0.6}>
        <Lightformer form="rect" intensity={4} position={[0, 8, 2]} scale={[14, 6, 1]} rotation-x={Math.PI / 2} />
        <Lightformer form="rect" intensity={2} position={[-9, 3, 4]} scale={[8, 4, 1]} rotation-y={Math.PI / 2} color="#ffe2b8" />
        <Lightformer form="rect" intensity={1.5} position={[9, 4, -4]} scale={[8, 4, 1]} rotation-y={-Math.PI / 2} />
      </Environment>
      <spotLight castShadow position={[-3, 15, 6]} angle={0.62} penumbra={0.85} intensity={420} color="#ffd9a6" shadow-mapSize={[2048, 2048]} shadow-bias={-0.0003} shadow-radius={6} />
      <ambientLight intensity={0.18} />
    </>
  )
}
