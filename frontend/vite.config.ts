import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    // src 直下がそのままドメイン名になっているため、`@/reservations` のように書ける。
    // 相対パスの `../../` を避けつつ、どのドメインを跨いだのかが import 文に出る。
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // ブラウザから見たオリジンを開発でも1つに保つための中継（authentication.md 12 節）。
    // これがあるおかげで CORS 設定が要らず、Cookie の SameSite も既定の Lax のままでよい。
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  // 出力先は既定の dist/ のまま。成果物を backend/user/src/main/resources/static/ へ入れる手段
  // （Gradle から npm run build を叩くか、CI で配置するか）は認証の設計とは独立として保留されている
  // （authentication.md 12 節「ビルドの統合」）ので、ここでは決め打ちにしない。
  //
  // 決めるときの制約が1つある。SecurityConfig が permitAll にしているのは `/`,
  // `/index.html`, `/favicon.ico`, `/assets/**` なので、build.assetsDir を assets から
  // 変えると本番で JS と CSS が 401 になる。
})
