// controller/src/tv/turf3d/ErrorBoundary.ts
import { Component, type ReactNode } from 'react'

interface Props { onError: () => void; children: ReactNode }
interface State { failed: boolean }

/** Catches a failed 3D chunk import or a WebGL context that would not start, so the TV falls back to the flat board. */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { failed: false }
  static getDerivedStateFromError(_error: unknown): State { return { failed: true } }
  componentDidCatch(error: Error): void {
    console.error('The 3D board failed; showing the flat board instead.', error)
    this.props.onError()
  }
  render(): ReactNode { return this.state.failed ? null : this.props.children }
}
