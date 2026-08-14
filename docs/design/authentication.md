# 認証・認可 設計

未実装。着手前に決めた方針を残したもの。実装時に前提が崩れたら、この文書も同じコミットで直すこと。

## 決定事項

| 論点 | 決定 | 却下した案 |
|---|---|---|
| 認証方式 | セッション（Cookie）+ Spring Session + Redis | JWT |
| 資格情報 | 自前でパスワードを保持 | 外部 IdP（OIDC） |
| ユーザー登録 | 自己サインアップ + メール検証 | 管理者招待 |
| 認可 | ロール（USER / ADMIN）+ Service 層での所有者チェック | `@PreAuthorize` 式による所有者判定 |

---

## 1. 認証方式

### セッションを選んだ理由

JWT の本来の利点は「受け取った側が発行元に問い合わせずに検証できる」ことで、これが効くのは複数サービスがトークンを持ち回る構成に限られる。単一のモノリスに対しては何も生まないまま、失効の問題だけを引き受けることになる。ログアウトしても権限を剥奪しても有効期限まで通り続けるため、結局ブラックリストを DB に持つはめになり、唯一の利点である「問い合わせ不要」が消える。

セッションなら失効は即座で、Spring Security の既定の経路にそのまま乗る。

引き受けたコストは **CSRF 対策が必要になること**。Cookie は自動送信されるため `CookieCsrfTokenRepository` を使い、フロントから `X-XSRF-TOKEN` ヘッダを返す実装が要る。JWT を `Authorization` ヘッダで送る方式ではこれが不要で、これが JWT 側の実質的な唯一の利点だった。

### Redis に逃がす理由

インメモリセッションではなく最初から Redis に置く。理由は水平スケールへの備えだけではなく、**アプリを再起動してもログインが切れない**こと。devtools 込みの開発では再起動が頻繁に起きるので、日常的に効く。

```yaml
spring:
  data:
    redis:
      host: redis        # .devcontainer/compose.yaml のサービス名
      port: 6379
  session:
    timeout: 30m
    redis:
      namespace: space-reserve:session
      flush-mode: on_save
```

`namespace` は明示する。後で Redis をキャッシュやレートリミットと共有したときにキーが混ざらないようにするため。

Compose 側は `--appendonly yes` で永続化する。これを外すと Redis コンテナの再起動でセッションが飛び、導入目的の半分が消える。

### シリアライズ

セッションが Redis に載るということは、`SecurityContext` ごとシリアライズされるということ。

- **`AppUserDetails` は `Serializable` を実装し、フィールドも全て Serializable にする。** 忘れるとインメモリでは動いていたものがログイン直後に `NotSerializableException` で落ちる。
- **既定は JDK シリアライズ**なので Redis の中身は目視できず、クラスにフィールドを1本足しただけで既存セッションが復元できなくなる。運用に乗せる前に `GenericJackson2JsonRedisSerializer` + `SecurityJackson2Modules` で JSON へ寄せる。開発初期は JDK シリアライズのままでよい。
- **devtools との相性に注意。** restart classloader で読み込まれたクラスと Redis 上のデータが食い違い、再起動後に復元エラーが出ることがある。開発中に不可解な例外が出たら `redis-cli FLUSHDB` を試す。

### 引き受けたリスク

**SPOF が1つ増える。** Redis が落ちるとセッションが読めず全ユーザーが実質ログアウトする。MySQL の停止はエラー応答として現れるが、Redis の場合は「気づいたら全員弾かれている」という形で表面化する。`/actuator/health` に Redis のヘルスが載ることを確認しておく。

---

## 2. パスワードの管理

### 保存

```java
@Bean
PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
}
```

**`BCryptPasswordEncoder` を直接返さない。** `DelegatingPasswordEncoder` はハッシュに `{bcrypt}` という識別子を前置して保存するため、後から Argon2 へ移行するときに既存ハッシュと新方式を同一カラムで共存させられる。直接指定すると移行時に全ユーザーへパスワード再設定を強いることになる。`password_hash` を `VARCHAR(255)` にしてあるのはこの前置き分の余裕。BCrypt 固定の 60 文字で切らないこと。

強度は既定（BCrypt strength 10）で始める。上げる場合、ログインのたびに走るハッシュ計算がそのままレスポンスタイムに乗ることを踏まえて実測してから決める。

### ポリシー

NIST SP 800-63B 以降の推奨に沿い、**長さを主とし、複雑性要求は課さない**。

```java
public record SignUpRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 12, max = 64) String password,
    @NotBlank @Size(max = 100) String displayName
) {}
```

- **最低 12 文字。** 「大文字・数字・記号を各1文字以上」といった構成要求は入れない。ユーザーを `Password1!` のような予測しやすい形に誘導し、実効エントロピーをむしろ下げるため。
- **上限は必須。** BCrypt は入力を **72 バイトで黙って切り捨てる**ため、上限がないと「73 文字目以降が違っても認証が通る」状態になる。マルチバイト文字ではバイト数で 64 文字でも超え得るので、厳密にやるならバイト長で検証する。
- **定期変更を強制しない。** 使い回しと単純な変形（`pass01` → `pass02`）を招くだけ。

将来的には Have I Been Pwned の Range API による漏洩パスワード照合が上記のどれより効果がある。パスワード全体を送らずハッシュ先頭5文字だけで照合できるため外部送信の懸念も小さい。外部依存が増えるので初期スコープからは外す。

### 絶対に守ること

- **`User` エンティティの `toString()` に `passwordHash` を含めない。** Lombok の `@Data` や IDE 生成の `toString` をそのまま使うと、エラーログにハッシュが流出する。
- **`LoginRequest` / `SignUpRequest` の `toString()` を明示的にオーバーライドする。** バリデーション失敗時に Spring がリクエストオブジェクトをログ出力する経路がある。
- **`spring.jpa.show-sql` を有効にしない。** パラメータバインディングのログにハッシュが乗る。`application.yaml` では現在無効なので、そのまま維持する。
- **`UserResponse` にパスワード関連のフィールドを作らない。**

---

## 3. ユーザー登録

自己サインアップを採る。管理者招待型と比べてメール送信基盤が初期スコープに入る分、作業量は 2〜3 ステップ増える。

### メール検証が必須である理由

自己サインアップの本質的な問題は、**登録者が「そのメールアドレスの持ち主である」ことを誰も確認していない**点にある。招待型ならこの確認は招待時点で済んでいる。

確認を省くと他人のアドレスで登録できてしまう。予約システムでこれが通ると、他人の名義で会議室を押さえられ、しかも本来の持ち主は**そのアドレスが既に使われているため自分では登録できない**（`uk_users_email` の一意制約）。単なるなりすましではなく、正規ユーザーの締め出しになる。

### フロー

```
POST /api/users {email, password, displayName}
  → 常に 202 Accepted（本文は「確認メールを送信しました」のみ）
```

**成否にかかわらず同じ応答を返す。** ここで「既に登録されています」と 409 を返すと、登録エンドポイントがそのままメールアドレスの在籍確認 API になる。誰でも叩けるため、招待型と違ってこれが現実的な脅威になる。

分岐は応答ではなくメールの内容で行う。

| 状況 | 送るメール |
|---|---|
| 未登録 | 検証リンク付きの登録確認メール |
| 登録済み・検証済み | 「既に登録があります。パスワードをお忘れならこちら」 |
| 登録済み・**未検証** | 既存レコードのパスワードを新しい入力で上書きし、トークンを再発行して再送 |

3行目がアドレス占拠への対策。**未検証のレコードは「まだ誰のものでもない」と扱い、上書きを許す。** これをしないと、攻撃者が他人のアドレスで登録しただけで本人が永久に登録できなくなる。

あわせて **24時間経過した未検証ユーザーは定期削除する。** Quartz を入れるほどではなく `@Scheduled` で足りる。

### 検証エンドポイント

```
POST /api/auth/verify {token}
```

メールのリンクは検証用フロントページに飛ばし、そこから POST させる。**GET で状態を変更しないこと。** メールクライアントやセキュリティ製品がリンクを先読みして GET を撃つことがあり、ユーザーが踏む前に検証が完了してしまう。

### トークンの扱い

`SecureRandom` で 32 バイト生成し、URL-safe Base64 でメールに載せる。**DB には SHA-256 ハッシュのみを保存する。** DB が漏れてもトークンとして使えないようにするため（パスワードと同じ理屈）。検証用の有効期限は 24 時間。

---

## 4. ログイン

`formLogin` は使わず、`AuthenticationManager` を注入した通常の `@RestController` から呼ぶ。リクエスト／レスポンスの形が既存の DTO 規約に揃うため。

```java
@PostMapping("/api/auth/login")
public UserResponse login(@Valid @RequestBody LoginRequest request,
                          HttpServletRequest httpRequest,
                          HttpServletResponse httpResponse) {
    Authentication auth = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                    request.email(), request.password()));
    // セッション固定攻撃対策。認証成功時に必ずセッション ID を振り直す。
    httpRequest.changeSessionId();
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, httpRequest, httpResponse);
    return UserResponse.from((AppUserDetails) auth.getPrincipal());
}
```

**`SecurityContextRepository` への保存を自分で書く必要がある。** `formLogin` を使わない代償で、これを忘れるとログインは成功するのにセッションに何も残らず、次のリクエストで 401 になる。最も引っかかりやすい箇所。

### 未検証チェックの順序 — ハマりどころ

`AppUserDetails.isEnabled()` を `enabled && emailVerified` にマップするのが自然に見えるが、**そのままだとメールアドレスの在籍確認ができてしまう。** `DaoAuthenticationProvider` は `preAuthenticationChecks`（有効・ロック・期限）を**パスワード照合より先に**実行するため、パスワードが間違っていても未検証アカウントには「無効です」と返り、アドレスの存在が漏れる。

有効性チェックをパスワード照合の後ろに移す。

```java
provider.setPreAuthenticationChecks(userDetails -> { /* 何もしない */ });
provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
```

パスワードが合った相手は既にアドレスとパスワードの両方を知っているので、そこで「メール未検証です」と伝えても追加で漏れる情報はない。この場合は検証メールの再送導線を返す。

### ユーザー列挙の防止

「メールアドレスが存在しない」と「パスワードが違う」で応答を変えない。`UserDetailsService` が `UsernameNotFoundException` を投げても Spring Security は既定で `BadCredentialsException` に隠蔽する（`hideUserNotFoundExceptions` が既定 true）。**ここを自分で握りつぶさないこと。**

応答時間の差でも漏れるため、厳密にやるならユーザーが存在しない場合もダミーハッシュに対して照合を走らせる。

---

## 5. 認可

### ロール

当面 `users.role` の単一カラム（`USER` / `ADMIN`）。1ユーザー1ロールで足りるうちはこれで十分で、複数ロールが必要になった時点で `user_roles` 中間テーブルへ切り出す。

### 判定をどこに書くか

- **ロールだけで決まる粗い制御**（`/api/admin/**` は ADMIN のみ）は `SecurityFilterChain` に宣言する。
- **所有者チェック**（「この予約を消せるのは本人か管理者だけ」）は **Service 層に書く。** 対象レコードを読まないと判定できないため。`@PreAuthorize` の式に押し込むとテストしづらく、`service/package-info.java` の「業務判断は Service」という方針とも衝突する。

### ログイン中のユーザー ID の取り出し

`UserDetails` を実装した `AppUserDetails` に `userId` を持たせ、Controller で `@AuthenticationPrincipal AppUserDetails user` として受ける。**Controller から Service へは `userId`（Long）だけを渡し、Spring Security の型を Service に持ち込まない。** Service を HTTP から独立させるという既存方針の延長。

---

## 6. データモデル

```sql
-- V1__create_users.sql
CREATE TABLE users (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  email          VARCHAR(255) NOT NULL,
  password_hash  VARCHAR(255) NOT NULL,
  display_name   VARCHAR(100) NOT NULL,
  role           VARCHAR(20)  NOT NULL,   -- USER / ADMIN
  email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
  enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at     DATETIME(6)  NOT NULL,
  updated_at     DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email)
);
```

```sql
-- V2__create_user_tokens.sql
CREATE TABLE user_tokens (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  purpose    VARCHAR(30)  NOT NULL,   -- EMAIL_VERIFICATION / PASSWORD_RESET
  token_hash CHAR(64)     NOT NULL,   -- 生トークンは保存しない
  expires_at DATETIME(6)  NOT NULL,
  used_at    DATETIME(6)  NULL,
  created_at DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_tokens_hash (token_hash),
  KEY idx_user_tokens_user_purpose (user_id, purpose),
  CONSTRAINT fk_user_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
```

補足：

- **これが最初のマイグレーションになる。** 予約系は `V3` 以降。`reservations.user_id` から `users.id` へ外部キーを張るため、この順序である必要がある。
- **`enabled` による論理削除。** 退職者をレコード削除すると、その人の過去の予約の外部キーが壊れる。
- **メール検証用とパスワードリセット用のトークンを1テーブルに相乗りさせている。** ハッシュ保存・期限・使用済み記録というライフサイクルが同一のため。
- **`ON DELETE CASCADE`** は、未検証ユーザーの定期削除でトークンごと消えるようにするため。
- **ログイン失敗回数はテーブルに持たない。** 理由は 8 節を参照。

---

## 7. パッケージ構成

既存のレイヤ構成に `security/` を1つ追加する。

```
com.example.spacereserve
├── domain/           User, Role(enum), UserToken, TokenPurpose(enum)
├── repository/       UserRepository, UserTokenRepository
├── service/          UserService, AuthService, MailService
├── controller/       AuthController, UserController
├── dto/request/      LoginRequest, SignUpRequest, VerifyRequest, ...
├── dto/response/     UserResponse
└── security/         ★新規
    ├── SecurityConfig                    SecurityFilterChain, PasswordEncoder
    ├── AppUserDetails                    UserDetails 実装（userId を保持、Serializable）
    ├── AppUserDetailsService             UserDetailsService 実装
    ├── ProblemAuthenticationEntryPoint   401 → ProblemDetail
    └── ProblemAccessDeniedHandler        403 → ProblemDetail
```

`config/` に混ぜず分けるのは、これらが業務ロジックでも単なる設定でもなく、**Spring Security という特定フレームワークへの適合層**だから。独立させておけば、認証方式を JWT や OIDC に変えるときの変更範囲がこのパッケージにほぼ閉じる。

`config/package-info.java` は「Spring Security の設定などが増えたらここに置く」と書いているが、実際に作るファイルが5個になるため方針を変えた。当該 package-info の記述は実装時に調整すること。

---

## 8. エラー応答

エラー応答は既存方針どおり RFC 9457 の Problem Details に統一する。**認証・認可の失敗は例外の発生場所によって処理経路が2つに分かれる**点に注意。

| 例外・事象 | 発生場所 | 処理 | ステータス |
|---|---|---|---|
| `BadCredentialsException` | ログイン Controller 内 | `GlobalExceptionHandler` | 401 |
| `TooManyAttemptsException`（自作） | ログイン Service 内 | `GlobalExceptionHandler` | 429 |
| `ForbiddenOperationException`（自作） | 各 Service の所有者チェック | `GlobalExceptionHandler` | 403 |
| 未認証で保護リソースへアクセス | `ExceptionTranslationFilter` | `AuthenticationEntryPoint` | 401 |
| ロール不足（`/api/admin/**` 等） | 同上 | `AccessDeniedHandler` | 403 |

**フィルタ層の 401/403 は `GlobalExceptionHandler` を通らない。** `@RestControllerAdvice` は DispatcherServlet 内部の例外しか拾えないが、保護リソースへの未認証アクセスはその手前のサーブレットフィルタで弾かれる。放置すると、Problem Details に統一したはずの応答のうち 401/403 だけが Spring 既定の別形式で返る。`security/` に置く `ProblemAuthenticationEntryPoint` / `ProblemAccessDeniedHandler` はこれを揃えるためのもので、省略できない。

**所有者チェックには Spring の `AccessDeniedException` を使わず自作例外を投げる。** `AccessDeniedException` を `@RestControllerAdvice` で捕まえると、`ExceptionTranslationFilter` が持つ「未認証ユーザーなら 403 ではなく 401 を返してログインへ誘導する」という分岐を横取りしてしまい、未ログイン時の挙動が壊れる。層ごとに例外の種類を分けておけばこの衝突が起きない。

**登録時の重複を 409 で返さない。** 3 節のとおり常に 202 を返す。

既存の `handleValidationFailure` が `errors` プロパティに項目別のマップを詰める形式は、登録フォームのバリデーションでもそのまま使える。

---

## 9. 総当たり・スパム対策

いずれも Redis の `INCR` + `EXPIRE` で実装する。1つの仕組みを用途別のキーで使い回す。

### ログインの総当たり

**アカウントロックは採らない。** 攻撃者が他人のメールアドレスで失敗を繰り返せばその人を締め出せてしまい、認証の穴が可用性の穴に化ける。

代わりに **メールアドレス + 送信元 IP のペアで試行回数を数え、閾値を超えたら一時拒否**する。

```
KEY  login:fail:{emailHash}:{ip}   TTL 15m
```

拒否時は 429。ここでも「どのアカウントが制限中か」を漏らさないよう、メッセージは一般的な文言に留める。

**失敗回数を `users` テーブルのカラムに持たせない。** ログイン失敗のたびに DB 書き込みが走るうえ、本質的に一時データをマスタに混ぜることになる。Redis がある構成ならそちらに置く方が素直。

### 登録スパム

自己サインアップは公開エンドポイントなので、ボットに叩かれる前提が要る。

- **IP 単位のレート制限** — 1 IP あたり 1時間に数件で十分。
- **メール送信自体のレート制限** — 同一アドレスへの再送は 60 秒間隔以上に。制限しないと、登録エンドポイントが第三者へのメール爆撃に使え、自分たちの送信ドメインの評判にも跳ね返る。
- **ドメイン許可リスト** — 社内利用が前提ならこれが最も効く。`@ConfigurationProperties` で許可ドメインを `application.yaml` から型付きで受ける。CAPTCHA を検討する前にまずこちらを検討すること。

---

## 10. パスワードの変更とリセット

### 変更（ログイン中）

**現在のパスワードを必ず要求する。** セッションを乗っ取られた場合にパスワードごと奪われるのを防ぐため。

変更成功後は **当該ユーザーの他のセッションを全て無効化する。** `SpringSessionBackedSessionRegistry` の `findByPrincipalName` で列挙して消せる。これは Spring Session + Redis 構成でこそ確実に効く処理で、インメモリセッションでは単一インスタンス内でしか効かない。

### リセット（パスワード忘れ）

トークン基盤（`user_tokens`）とメール送信がメール検証で揃うため、追加コストは小さい。

- 有効期限 30分〜1時間、`used_at` で使用済みを潰す。
- **未登録のアドレスに対しても成功応答を返す。** そうしないと在籍確認に使える。
- リセット完了時に既存セッションを全て無効化する。

### 同時ログイン数の制御（任意）

`SpringSessionBackedSessionRegistry` を `sessionManagement().maximumSessions(1)` と組めば「1ユーザー1セッション、新しいログインで古い方を無効化」が実現できる。Redis 化の副産物。必要になったら入れる。

---

## 11. 依存とインフラ

### Gradle

```kotlin
implementation("org.springframework.boot:spring-boot-starter-security")
implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
implementation("org.springframework.boot:spring-boot-starter-mail")
testImplementation("org.springframework.boot:spring-boot-starter-security-test")
testImplementation("org.springframework.boot:spring-boot-starter-session-data-redis-test")
testImplementation("org.springframework.boot:spring-boot-starter-mail-test")
```

いずれも Boot 4.1 の BOM で管理されているためバージョン指定は不要（BOM の実物で確認済み）。Boot 3 系では Redis セッションに `spring-boot-starter-data-redis` と `spring-session-data-redis` の2本が要ったが、4.1 では `spring-boot-starter-session-data-redis` 1本にまとまっている。`build.gradle.kts` の既存コメントが指摘しているとおり、Boot 4 では自動設定が技術ごとのモジュールに分かれているので、必ず starter を経由すること。

### Compose に追加するサービス

```yaml
  redis:
    image: redis:8        # 確認したパッチタグまで固定する
    command: ["redis-server", "--appendonly", "yes"]
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 20

  mailpit:
    image: axllent/mailpit:latest   # 同上、実際のタグに固定する
    ports:
      - "18025:8025"   # Web UI
```

`app` の `depends_on` に `redis: {condition: service_healthy}` を追加し、`volumes:` に `redis-data:` を足す。ホストへの公開は MySQL を 13306 にずらしているのと同じ方針（Redis を見たければ `16379:6379`）。

メールは開発中 mailpit が受けて捨てるため外部へ出ない。`spring.mail.host: mailpit` / `port: 1025` を向ける。

### Testcontainers

**Testcontainers 2.0.5 に Redis 専用モジュールは存在しない**（BOM の収録モジュール一覧で確認済み。`redpanda` はあるが別物）。既存の `MySQLContainer` と同じノリで書こうとすると詰まる。コアの `GenericContainer` を使い、サービス名を明示する。

```java
@Bean
@ServiceConnection("redis")
GenericContainer<?> redisContainer() {
    return new GenericContainer<>(DockerImageName.parse("redis:8"))
            .withExposedPorts(6379);
}
```

`@ServiceConnection` に `"redis"` を渡すのは、`GenericContainer` からは接続の種類を推論できないため（`MySQLContainer` は型から自明なので引数不要）。追加依存は不要でコアの `testcontainers` に含まれる。

> **未検証**：この配線は Boot 4.1 の data-redis 自動設定モジュール側の実装に依存する。該当 jar が手元に無く確認できていないため、実装時に最初の `contextLoads` テストで疎通を確かめること。

テストが `@SpringBootTest` のたびに MySQL と Redis の2コンテナを起動するようになるため、実行時間は延びる。気になったら Testcontainers の再利用（`.withReuse(true)`）を検討する。

### メールテンプレート

HTML メールを Thymeleaf で組むなら `src/main/resources/templates/` を使う。REST API なので画面用テンプレートは不要だが、この用途で残る。`src/main/resources/static/` は使わない。

---

## 12. 実装順

1. 依存追加（security / session-data-redis / mail）+ `SecurityConfig`（全エンドポイントを一旦保護して動作確認）
2. `V1__create_users.sql` + `User` + `Role` + `UserRepository`
3. `AppUserDetails` + `AppUserDetailsService`（**認証チェック順の入れ替え込み**）
4. `/api/auth/login` `/logout` `/me` + Spring Session / Redis の疎通確認
5. 401/403 の Problem Details 化（**忘れやすいので独立ステップにする**）
6. Compose に mailpit + メール送信の土台
7. `V2__create_user_tokens.sql` + ユーザー登録 + メール検証
8. レート制限（Redis）+ 未検証ユーザーの定期削除
9. パスワード変更・リセット
10. CSRF 設定とフロントとの疎通
11. ロールベースの認可、その後で予約側の所有者チェック

各段階で `@WithMockUser` を使ったテストを足していけば、Testcontainers の MySQL に対して実際のマイグレーションごと検証できる。

---

## 将来の検討事項

いずれも初期スコープ外。必要になった時点で判断する。

- **漏洩パスワード照合**（Have I Been Pwned Range API）— ポリシー強化として最も費用対効果が高い。
- **多要素認証（TOTP）** — 自前パスワード管理を続けるなら、いずれ必要になる。
- **外部 IdP（OIDC）への移行** — 運用が重くなったら。`security/` パッケージを分離してあるのは、この移行の変更範囲を閉じ込めるため。
- **セッションの JSON シリアライズ化** — 運用に乗せる前に対応する。
- **同時ログイン数の制御** — 不正利用の兆候が出たら。
