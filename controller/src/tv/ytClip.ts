export interface ClipPlayer {
  /** Starts the video playing from [startSec]. */
  load(videoId: string, startSec: number): void
  /** Plays the loaded video again from [startSec]. */
  seek(startSec: number): void
  resume(): void
  pause(): void
  mute(on: boolean): void
  setVolume(v: number): void
  destroy(): void
}
export interface ClipEvents { onPlaying(): void; onError(code: number): void; onBlocked(): void }
export type MakePlayer = (host: HTMLElement, ev: ClipEvents) => Promise<ClipPlayer>

interface YtPlayer {
  loadVideoById(o: { videoId: string; startSeconds: number }): void
  seekTo(s: number, ahead: boolean): void
  playVideo(): void
  pauseVideo(): void
  mute(): void
  unMute(): void
  setVolume(v: number): void
  destroy(): void
}
interface YtApi { Player: new (el: HTMLElement, o: Record<string, unknown>) => YtPlayer }
const w = window as unknown as { YT?: YtApi; onYouTubeIframeAPIReady?: () => void }

let api: Promise<YtApi> | null = null
function loadApi(): Promise<YtApi> {
  if (w.YT?.Player) return Promise.resolve(w.YT)
  api ??= new Promise<YtApi>((resolve, reject) => {
    const earlier = w.onYouTubeIframeAPIReady
    w.onYouTubeIframeAPIReady = () => { earlier?.(); resolve(w.YT!) }
    const s = document.createElement('script')
    s.src = 'https://www.youtube.com/iframe_api'
    s.onerror = () => { api = null; reject(new Error('the YouTube player script did not load')) }
    document.head.appendChild(s)
  })
  return api
}

/** The real YouTube IFrame player, filling [host]. Resolves once the player is ready for commands. */
export const makeYouTubePlayer: MakePlayer = async (host, ev) => {
  const YT = await loadApi()
  const slot = document.createElement('div')
  host.appendChild(slot)
  return new Promise<ClipPlayer>((resolve, reject) => {
    let p: YtPlayer
    try {
      p = new YT.Player(slot, {
        width: '100%', height: '100%',
        playerVars: { controls: 0, disablekb: 1, playsinline: 1, rel: 0, iv_load_policy: 3, fs: 0, origin: location.origin },
        events: {
          onReady: () => resolve({
            load: (videoId, startSec) => p.loadVideoById({ videoId, startSeconds: startSec }),
            seek: (startSec) => { p.seekTo(startSec, true); p.playVideo() },
            resume: () => p.playVideo(),
            pause: () => p.pauseVideo(),
            mute: (on) => (on ? p.mute() : p.unMute()),
            setVolume: (v) => p.setVolume(v),
            destroy: () => p.destroy(),
          }),
          onStateChange: (e: { data: number }) => { if (e.data === 1) ev.onPlaying() },
          onError: (e: { data: number }) => ev.onError(e.data),
          onAutoplayBlocked: () => ev.onBlocked(),
        },
      })
    } catch (err) { reject(err) }
  })
}

/** A player that "plays" 100 ms after it is told to, so tests need no network. A video id starting with `bad` errors like YouTube's 150. */
export const makeFakePlayer: MakePlayer = async (_host, ev) => {
  let timer: ReturnType<typeof setTimeout> | null = null
  let current = ''
  const soon = () => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => (current.startsWith('bad') ? ev.onError(150) : ev.onPlaying()), 100)
  }
  return {
    load: (videoId) => { current = videoId; soon() },
    seek: () => soon(),
    resume: () => undefined,
    pause: () => undefined,
    mute: () => undefined,
    setVolume: () => undefined,
    destroy: () => { if (timer) clearTimeout(timer) },
  }
}

export const chooseMaker = (): MakePlayer => (new URLSearchParams(location.search).get('yt') === 'fake' ? makeFakePlayer : makeYouTubePlayer)
