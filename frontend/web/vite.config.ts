import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Requests to /api go to Spring Boot. The browser only ever talks to localhost:5173,
    // so it's the "same origin" and no CORS setup is needed while developing.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
