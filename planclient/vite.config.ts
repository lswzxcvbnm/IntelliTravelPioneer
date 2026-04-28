import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { API_BASE_URL } from './src/config/api'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: API_BASE_URL,
        changeOrigin: true,
        secure: false
      }
    }
  },
  build: {
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('element-plus')) {
              return 'vendor-element-plus'
            }
            if (id.includes('vue') || id.includes('@vue')) {
              return 'vendor-vue'
            }
            if (id.includes('axios')) {
              return 'vendor-axios'
            }
            return 'vendor'
          }
          if (id.includes('src/views/')) {
            const match = id.match(/src\/views\/([^\/]+)\//)
            if (match && match[1]) {
              return `view-${match[1]}`
            }
          }
        }
      }
    }
  }
})
