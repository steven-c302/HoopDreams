import { StrictMode, Suspense, lazy } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router'
import './index.css'
import App from './App.tsx'

const PlayPage = lazy(() => import('./play/PlayPage.tsx'))
const TvPage = lazy(() => import('./tv/TvPage.tsx'))

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Suspense fallback={null}>
        <Routes>
          <Route path="/" element={<App />} />
          <Route path="/play" element={<PlayPage />} />
          <Route path="/tv" element={<TvPage />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  </StrictMode>,
)
