import { StrictMode, Suspense, lazy } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router'
import './index.css'
import App from './App.tsx'
import NightScreen from './gamenight/NightScreen'

const PlayPage = lazy(() => import('./play/PlayPage.tsx'))
const TvPage = lazy(() => import('./tv/TvPage.tsx'))
const HostPage = lazy(() => import('./host/HostPage.tsx'))

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Suspense fallback={null}>
        <Routes>
          <Route path="/" element={<NightScreen />} />
          <Route path="/hat" element={<App />} />
          <Route path="/play" element={<PlayPage />} />
          <Route path="/tv" element={<NightScreen />} />
          <Route path="/tracker" element={<TvPage />} />
          <Route path="/host" element={<HostPage />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  </StrictMode>,
)
