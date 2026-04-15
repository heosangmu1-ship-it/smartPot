import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    // Spring Boot 의 src/main/resources/static 으로 바로 빌드
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    // dev 모드: /api 는 로컬 Spring Boot 로 프록시
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
