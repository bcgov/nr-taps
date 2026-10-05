import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import { tanstackRouter } from '@tanstack/router-plugin/vite'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  // The dev server settings come from .env files or the container environment.
  const env = loadEnv(mode, process.cwd(), '')
  const devHost = env.VITE_DEV_HOST ?? 'localhost'
  const devPort = Number(env.VITE_DEV_PORT ?? 3000)
  const backendTarget = env.VITE_DEV_BACKEND_TARGET ?? 'http://localhost:8080'

  return {
    plugins: [
      tanstackRouter({
        target: 'react',
        autoCodeSplitting: true,
      }),
      react(),
    ],
    server: {
      host: devHost,
      port: devPort,
      fs: {
        // Allow serving files from one level up to the project root
        allow: ['..'],
      },
      proxy: {
        '/api': {
          target: backendTarget,
          changeOrigin: true,
          secure: false,
        },
      },
    },
    preview: {
      host: devHost,
      port: devPort,
    },
    resolve: {
      // https://vitejs.dev/config/shared-options.html#resolve-alias
      tsconfigPaths: true,
      extensions: ['.js', '.json', '.jsx', '.mjs', '.ts', '.tsx'],
    },
    build: {
      // Build Target
      // https://vitejs.dev/config/build-options.html#build-target
      target: 'esnext',
      // Rollup Options
      // https://vitejs.dev/config/build-options.html#build-rollupoptions
      rollupOptions: {},
    },
    css: {
      preprocessorOptions: {
        scss: { quietDeps: true },
      },
    },
  }
})
