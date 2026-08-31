# spaces/

スペースドメイン。**まだ空。** ディレクトリと `routes/spaceRoutes.tsx` の口だけ用意してある。

`app/router.tsx` はすでに `spaceRoutes` を展開しているため、画面を足すときに配線を
触る必要は無い。`routes/spaceRoutes.tsx` の配列に `RouteObject` を追加し、公開するものを
`index.ts` に足す。

中身の切り方と禁止事項は `src/README.md` を参照。要点だけ再掲する。

- `api/` と `model/` は React を import しない。
- 他ドメインを触るときは相手の `index.ts` 経由に限る。
- サーバのエラーは `shared/api` の `toApiFailure` で均し、表示文言はこのドメインが持つ。
