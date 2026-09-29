import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// In dev, the Mac devserver (./gradlew :devserver:run) answers /api and /ws.
export default defineConfig({
  plugins: [react()],
  build: { outDir: 'dist', assetsDir: 'assets', sourcemap: false },
  server: {
    host: true,
    proxy: {
      '/api': 'http://127.0.0.1:8080',
      '/ws': { target: 'ws://127.0.0.1:8080', ws: true },
    },
  },
  test: { include: ['src/**/*.test.ts'] },
})
