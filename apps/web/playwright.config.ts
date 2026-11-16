import { defineConfig, devices } from '@playwright/test';
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  timeout: 60000,
  expect: { timeout: 15_000 },
  use: {
    baseURL: 'http://127.0.0.1:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    navigationTimeout: 60_000,
    actionTimeout: 15_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 1000 } },
    },
  ],
  webServer: [
    {
      command: 'npm run dev:web',
      cwd: '../..',
      url: 'http://127.0.0.1:5173',
      reuseExistingServer: !process.env.CI,
      timeout: 60000,
    },
    {
      command: 'npx expo start --web --port 8081',
      cwd: '../mobile',
      url: 'http://127.0.0.1:8081',
      reuseExistingServer: !process.env.CI,
      timeout: 120000,
      env: {
        EXPO_PUBLIC_API_URL: 'http://127.0.0.1:19080/api/v1',
        EXPO_PUBLIC_WEB_URL: 'http://127.0.0.1:5173',
      },
    },
  ],
});
