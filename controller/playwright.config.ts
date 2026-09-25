import { defineConfig, devices } from '@playwright/test'

const javaHome = process.env.JAVA_HOME ?? '/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home'

// Runs the real Kotlin server (tv/devserver) serving this app's production build.
export default defineConfig({
  testDir: 'e2e',
  timeout: 90_000,
  use: { baseURL: 'http://127.0.0.1:8090', ...devices['Pixel 7'] },
  webServer: {
    command: `JAVA_HOME=${javaHome} ../tv/devserver/build/install/devserver/bin/devserver --port 8090 --pin 4242 --static dist --bind 127.0.0.1`,
    url: 'http://127.0.0.1:8090/healthz',
    reuseExistingServer: false,
    timeout: 60_000,
  },
})
