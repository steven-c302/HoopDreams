// controller/src/tv/turf3d/ErrorBoundary.test.ts
import { describe, expect, it, vi } from 'vitest'
import { ErrorBoundary } from './ErrorBoundary'

describe('ErrorBoundary', () => {
  it('turns a render error into the failed state and tells the caller once', () => {
    const onError = vi.fn()
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const boundary = new ErrorBoundary({ onError, children: null })
    expect(ErrorBoundary.getDerivedStateFromError(new Error('chunk failed'))).toEqual({ failed: true })
    boundary.componentDidCatch(new Error('chunk failed'))
    expect(onError).toHaveBeenCalledTimes(1)
  })

  it('shows its children until something fails, and nothing after', () => {
    const boundary = new ErrorBoundary({ onError: () => undefined, children: 'the board' })
    expect(boundary.render()).toBe('the board')
    boundary.state = { failed: true }
    expect(boundary.render()).toBeNull()
  })
})
