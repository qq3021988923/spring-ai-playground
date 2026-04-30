import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3002,
    proxy: {
      '/ai': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        rewrite: (path) => path
      }
    }
  }
})
