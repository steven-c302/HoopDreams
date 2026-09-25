import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const backend = 'http://127.0.0.1:8000'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Party traffic goes to FastAPI. Steven's hat API is still called directly via VITE_API_URL.
    proxy: {
      '/api/party': backend,
      '/socket.io': { target: backend, ws: true },
      '/media': backend,
    },
  },
})
