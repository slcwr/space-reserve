# space-reserve

Spring Boot 4.1 / Java 21 / Gradle (Kotlin DSL) / MySQL 8.4。
開発環境は Dev Container + Docker Compose 構成。

## 構成

| 要素 | 選択 |
|---|---|
| コンテナ | Docker Compose（`app` + `db`）を Dev Container から参照 |
| ベースイメージ | `mcr.microsoft.com/devcontainers/java:3-21-trixie` |
| ビルド | Gradle 9.5.1（Kotlin DSL）、wrapper 経由 |
| DB（開発時） | Compose 常駐の MySQL 8.4（ホスト名 `db`） |
| DB（テスト時） | Testcontainers が起動する MySQL 8.4 |

開発時とテスト時で DB の供給源を分けている。前者は起動が速くデータが残る、
後者はテストごとに使い捨てでき CI でもそのまま動く、という使い分け。

## パッケージ構成

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

## スキーマ管理

Flyway を使う。マイグレーションは `src/main/resources/db/migration/` に置き、
規約は同ディレクトリの [README](src/main/resources/db/migration/README.md) を参照。

`spring.jpa.hibernate.ddl-auto` は `validate` にしてあるため、エンティティと
スキーマがずれるとアプリが起動しない。エンティティを追加・変更したら、
同じコミットでマイグレーションも書くこと。

## 起動

VS Code で「Reopen in Container」。初回は `postCreateCommand` が
Gradle キャッシュの所有権調整と依存のウォームアップを行う。

```bash
./gradlew bootRun       # Compose の MySQL に接続して起動
./gradlew bootTestRun   # Testcontainers の MySQL を立てて起動
./gradlew test          # Testcontainers を使ってテスト
```

- アプリ: http://localhost:8080 （ヘルスチェックは `/actuator/health`）
- MySQL: ホストからは `localhost:13306`（user/password ともに `app`、DB は `space_reserve`）

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
