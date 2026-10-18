import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'
import { resolve } from 'path'

// 显式加载 .env 文件
const env = loadEnv(process.env.NODE_ENV || 'development', process.cwd(), '')
// console.log('VITE_API_BASE_URL:', env.VITE_API_BASE_URL)
// console.log('Loaded env keys:', Object.keys(env).filter(key => key.startsWith('VITE_')))

export default defineConfig({
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      injectRegister: 'script',
      includeAssets: ['favicon.ico', 'icons/*.png'],
      manifest: {
        name: 'Golden Finance',
        short_name: 'Golden',
        description: '个人财务管理工具',
        theme_color: '#1989fa',
        background_color: '#ffffff',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        orientation: 'portrait',
        lang: 'zh-CN',
        icons: [
          {
            src: '/icons/pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png',
            purpose: 'any maskable'
          },
          {
            src: '/icons/pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable'
          }
        ]
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
        // ⚠ 刻意**不配置 runtimeCaching**（2026-10-08 安全整改 P0-8 已移除）
        //
        // 原先有一条规则把 `/api/v1/` 的响应按 NetworkFirst 缓存进 Cache Storage
        // （cacheName: 'api-cache'，maxAge 24 小时、100 条）。
        // 问题：这是**财务隐私数据**（流水、账单、余额、用户信息），会被明文写进
        // 浏览器磁盘；且原登出与 401 流程都不清它 ⇒ 登出后数据仍留在本机可被读取。
        //
        // 结论：API 响应一律走网络，不进缓存。
        // 若日后确需离线能力，请**逐接口白名单**添加，且必须排除含敏感字段的路径，
        // 绝不要用 `/api/v1/` 这种通配规则。
        runtimeCaching: [],
      },
    }),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: Number(env.VITE_PORT) || 5173,
    proxy: {
      '/api/v1': {
        target: env.VITE_API_BASE_URL || 'http://localhost:3001',
        changeOrigin: true,
      },
    },
  },
})