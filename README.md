# space-reserve

Spring Boot 4.1 / Java 21 / Gradle (Kotlin DSL) / MySQL 8.4。
開発環境は Dev Container + Docker Compose 構成。

## 構成

| 要素 | 選択 |
|---|---|
| リポジトリ | `backend/`（Gradle）と `frontend/`（npm）を並置。ルートにビルドファイルは置かない |
| コンテナ | Docker Compose（`app` + `db`）を Dev Container から参照 |
| ベースイメージ | `mcr.microsoft.com/devcontainers/java:3-21-trixie` |
| ビルド | Gradle 9.5.1（Kotlin DSL）、wrapper 経由 |
| DB（開発時） | Compose 常駐の MySQL 8.4（ホスト名 `db`） |
| DB（テスト時） | Testcontainers が起動する MySQL 8.4 |
| フロントエンド | React + TypeScript + Vite（`frontend/`） |

開発時とテスト時で DB の供給源を分けている。前者は起動が速くデータが残る、
後者はテストごとに使い捨てでき CI でもそのまま動く、という使い分け。

## ディレクトリ構成

```
space-reserve/
├── backend/        Spring Boot。wrapper と settings.gradle.kts もこの中
├── frontend/       React + TypeScript + Vite
├── docs/design/    設計メモ
└── .devcontainer/
```

**ルートに Gradle のファイルは置いていない。** マルチプロジェクトにせず `backend/` を独立した
Gradle プロジェクトにしているのは、`frontend/` が独立した npm プロジェクトであるのと対称に
するため。どちらも自分のディレクトリの中で完結し、ルートは両者を並べるだけの場所になる。

そのため Gradle のコマンドは `backend/` の中で叩く。

## バックエンドのパッケージ構成

REST API 構成（サーバサイドレンダリングは行わない）で、レイヤ優先に切っている。
各パッケージの責務と禁止事項は `package-info.java` に書いてあるので、
迷ったらそちらを参照する。

```
com.example.spacereserve
├── controller/    HTTP の境界。@RestController
├── service/       業務ルールとトランザクション境界。@Transactional はここ
├── repository/    永続化。JpaRepository のインターフェースのみ
├── domain/        JPA エンティティ、列挙、値オブジェクト
├── dto/
│   ├── request/   リクエストボディ。Bean Validation はここ
│   └── response/  レスポンスボディ。REST におけるビュー
├── config/        @Configuration 置き場
└── exception/     業務例外と GlobalExceptionHandler
```

依存の向きは `controller → service → repository` の一方向。逆流させない。
エンティティは `service` と `repository` の内側に閉じ、`controller` は DTO しか扱わない。

エラー応答は RFC 9457 の Problem Details（`application/problem+json`）に統一している。
独自のエラー DTO は作らず、`ProblemDetail` を使う。

## フロントエンド

`frontend/` に React + TypeScript + Vite の SPA を置く。サーバサイドレンダリングは行わず、
API は同一オリジンの `/api` を叩く。詳細は [frontend/README.md](frontend/README.md)。

ディレクトリはドメイン優先（Screaming Architecture）で切る。`src/` を開いた人が最初に
読み取るべきなのは「React アプリである」ことではなく「スペース予約システムである」こと。

```
frontend/src/
├── authentication/   ログイン・ログアウト・現在ユーザー
├── reservations/     予約
├── spaces/           スペース
├── users/            利用者
├── shared/           横断的な部品（apiClient, 共通 UI）
└── app/              起動と配線（ルータ、プロバイダ）
```

サーバがレイヤ優先なのに対しこちらがドメイン優先なのは意図的で、サーバは1つの HTTP 境界を
層で守る作りなのに対し、画面は機能単位で足し引きされるため。各ディレクトリの責務と禁止事項は
`README.md` に書いてある（サーバ側の `package-info.java` と同じ役割）。迷ったら
[frontend/src/README.md](frontend/src/README.md) を参照する。

## スキーマ管理

Flyway を使う。マイグレーションは `backend/src/main/resources/db/migration/` に置き、
規約は同ディレクトリの [README](backend/src/main/resources/db/migration/README.md) を参照。

`spring.jpa.hibernate.ddl-auto` は `validate` にしてあるため、エンティティと
スキーマがずれるとアプリが起動しない。エンティティを追加・変更したら、
同じコミットでマイグレーションも書くこと。

## 起動

VS Code で「Reopen in Container」。初回は `postCreateCommand` が
Gradle キャッシュの所有権調整と依存のウォームアップを行う。

```bash
cd backend
./gradlew bootRun       # Compose の MySQL に接続して起動
./gradlew bootTestRun   # Testcontainers の MySQL を立てて起動
./gradlew test          # Testcontainers を使ってテスト
```

```bash
npm --prefix frontend run dev   # Vite dev server
```

画面を触るときは `bootRun` と `npm run dev` の両方を上げる。ブラウザで開くのは Vite 側
（`:5173`）で、`/api` はそこから `:8080` へ中継される。ブラウザから見たオリジンを1つに保つ
ためで、これにより CORS 設定は開発・本番のどちらでも要らない。

- アプリ: http://localhost:8080 （ヘルスチェックは `/actuator/health`）
- 画面（開発時）: http://localhost:5173
- MySQL: ホストからは `localhost:13306`（user/password ともに `app`、DB は `space_reserve`）

## 整形

Java の整形は [spring-javaformat](https://github.com/spring-io/spring-javaformat) に任せる。
Spring Boot 本体と同じ規約（タブ4・120桁・import 順）で、設定項目は持たない。

```bash
cd backend
./gradlew format       # 整形する
./gradlew checkFormat  # 崩れていないか検査する（check に紐づくので test でも走る）
```

**コメントは整形のたびに幅いっぱいまで詰め直される。** 日本語も1文字を1桁として数えるため、
手で折り返しても次の `format` で1行にまとめられる。1文1行で書き、読点で改行しないこと。

VS Code で保存時に整形したい場合は公式拡張を入れる。Marketplace には無く、VSIX を
Maven Central から取ってきて拡張パネルの「Install from VSIX」で選ぶ。

```bash
curl -LO https://repo1.maven.org/maven2/io/spring/javaformat/spring-javaformat-vscode-extension/0.0.48/spring-javaformat-vscode-extension-0.0.48.vsix
```

`devcontainer.json` で `java.format.enabled` を `false` にしてあるのは、Java 拡張の
既定フォーマッタ（スペース4）と競合させないため。

## 構成上の注意点

**Testcontainers の接続先** — Dev Container はホストの Docker socket を借りているため、
Testcontainers が起動するコンテナは `app` の子ではなく兄弟になる。`app` からそれらに
到達するには公開ポートをホスト経由で叩く必要があるので、`compose.yaml` で
`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` と `extra_hosts` を設定している。

**ベースイメージの系列** — `bookworm` 系タグは同梱の yarn apt リポジトリの署名鍵が失効しており、
feature のインストール中に `apt-get update` が失敗する。そのため `trixie` 系を使い、
`docker-outside-of-docker` feature は `moby: false`（trixie に `moby-cli` が無い）としている。

**スキーマ管理** — `spring.jpa.hibernate.ddl-auto` は `validate`。エンティティを追加する際は
Flyway なりのマイグレーションツールを別途導入する前提。

**Gradle の置き場** — wrapper は `backend/gradlew` にしかない。ルートから `./gradlew` は叩けない。
`.gitattributes` の改行コード指定と `postCreateCommand` のウォームアップも `backend/` を指している。
