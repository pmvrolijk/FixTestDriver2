import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  // sockjs-client references the Node `global` object, which Vite's dev server
  // does not polyfill. Without this, sockjs-client throws
  // `ReferenceError: global is not defined` at module-eval time, aborting the
  // whole module graph so React never mounts (blank/black screen).
  define: {
    global: 'globalThis',
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws-fix': {
        target: 'http://localhost:8080',
        ws: true,
      },
    },
  },
})

