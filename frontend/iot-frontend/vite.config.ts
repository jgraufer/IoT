import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  resolve: {
    alias: {
      '@target': fileURLToPath(
        new URL(`./src/target/${mode === 'edge' ? 'edge' : 'cloud'}`, import.meta.url),
      ),
    },
  },
}))
