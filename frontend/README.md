# frontend

React + TypeScript + Vite。SPA として単体で配信し、API は同一オリジンの `/api` を叩く。

構成の考え方とディレクトリの規約は [src/README.md](src/README.md)。サーバとの接続の設計は
[docs/design/authentication.md](../docs/design/authentication.md) の 12 節。

## 使うもの

| | 選択 | 理由 |
|---|---|---|
| ビルド | Vite | |
| ルーティング | react-router | |
| サーバ状態 | TanStack Query | 取得・キャッシュ・再取得の定型を1つに寄せる |
| HTTP | axios | `XSRF-TOKEN` を `X-XSRF-TOKEN` に載せ替える処理を既定で持つ |
| クライアント状態 | （なし） | サーバ状態はクエリキャッシュ、画面固有は `useState` で足りる |

`fetch` ではなく axios なのは CSRF のため。`fetch` だと Cookie を読んでヘッダに載せる処理を
毎回自前で書くことになる（authentication.md 12 節）。

## 起動

Node が要る。Dev Container には `devcontainer.json` の node feature で入る。

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

**バックエンドを別に起動しておくこと。** `/api` は Vite の proxy が `:8080` へ中継する。

```bash
cd backend && ./gradlew bootRun
```

proxy を挟むのは、ブラウザから見たオリジンを開発でも1つに保つため。これにより CORS 設定は
開発・本番のどちらでも要らず、セッション Cookie の `SameSite` は既定の `Lax` のままでよい。

```bash
npm run build      # tsc -b && vite build → dist/
npm run lint       # oxlint
```

## ビルド成果物の配置

本番は Spring が `backend/src/main/resources/static/` から配信する。**`dist/` をそこへ入れる手段
（Gradle から `npm run build` を叩くか、CI で配置するか）はまだ決めていない**
（authentication.md 12 節「ビルドの統合」）。開発中は Vite dev server を使うため、この統合が
無くても支障は無い。

決めるときの制約が1つある。`SecurityConfig` が `permitAll` にしているのは `/`,
`/index.html`, `/favicon.ico`, `/assets/**` なので、`build.assetsDir` を `assets` から変えると
本番で JS と CSS が 401 になる。
