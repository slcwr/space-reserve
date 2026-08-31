# 認証・認可 設計

未実装。着手前に決めた方針を残したもの。実装時に前提が崩れたら、この文書も同じコミットで直すこと。

実行時に何がどの順で動くかは [authentication-flow.md](authentication-flow.md) に図で示した。

## 決定事項

| 論点 | 決定 | 却下した案 |
|---|---|---|
| 認証方式 | セッション（Cookie）+ Spring Session + Redis | JWT |
| 資格情報 | 自前でパスワードを保持 | 外部 IdP（OIDC） |
| ユーザー登録 | 自己サインアップ（**メール検証なし**）+ ドメイン許可リスト | 管理者招待 / メール検証あり |
| パスワードリセット | 管理者による再発行 | メールによる自己リセット |
| 認可 | ロール（USER / ADMIN）+ Service 層での所有者チェック | `@PreAuthorize` 式による所有者判定 |
| フロントとの配置 | React を**同一オリジン**で配信（開発は Vite の proxy、本番は Spring から静的配信） | 別オリジン + CORS |

**メール送信基盤は初期スコープに含めない。** 理由と引き受けたリスクは 3 節を参照。

---

## 1. 認証方式

### セッションを選んだ理由

JWT の本来の利点は「受け取った側が発行元に問い合わせずに検証できる」ことで、これが効くのは複数サービスがトークンを持ち回る構成に限られる。単一のモノリスに対しては何も生まないまま、失効の問題だけを引き受けることになる。ログアウトしても権限を剥奪しても有効期限まで通り続けるため、結局ブラックリストを DB に持つはめになり、唯一の利点である「問い合わせ不要」が消える。

セッションなら失効は即座で、Spring Security の既定の経路にそのまま乗る。

引き受けたコストは **CSRF 対策が必要になること**。Cookie は自動送信されるため `CookieCsrfTokenRepository` を使い、フロントから `X-XSRF-TOKEN` ヘッダを返す実装が要る。JWT を `Authorization` ヘッダで送る方式ではこれが不要で、これが JWT 側の実質的な唯一の利点だった。具体的な設定は 12 節。

**`csrf.disable()` は選択肢に入らない。** 「REST API だから CSRF 不要」が成り立つのは認証情報を `Authorization` ヘッダで送る場合だけで、セッション Cookie を選んだ本構成では前提が成立しない。無効化すると「ログイン中のユーザーに罠サイトを踏ませて勝手に予約させる／他人の予約を削除させる」が成立する。

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
    data:                    # Boot 4.0 で spring.session.redis.* から移動
      redis:
        namespace: space-reserve:session
        flush-mode: on-save
```

`namespace` は明示する。後で Redis をキャッシュやレートリミットと共有したときにキーが混ざらないようにするため。

**プロパティの位置に注意。** Boot 4.0 で `spring.session.redis.*` は `spring.session.data.redis.*` へ移った（メタデータ上の deprecation level は `error`）。旧名を書いても解決されず**黙って無視される**ため、エラーも警告も出ないまま `namespace` が既定の `spring:session` に戻る。`redis-cli KEYS` を叩くまで気づけない類なので、`RedisSessionNamespaceTests` が実際のキーで固定している。接続先の `spring.data.redis.*` と `spring.session.timeout` は Boot 4 でもそのまま。

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

- **`User` モデルの `toString()` に `passwordHash` を含めない。** Lombok の `@Data` や IDE 生成の `toString` をそのまま使うと、エラーログにハッシュが流出する。
- **`LoginRequest` / `SignUpRequest` の `toString()` を明示的にオーバーライドする。** バリデーション失敗時に Spring がリクエストオブジェクトをログ出力する経路がある。
- **`repository` パッケージのログレベルを本番で `DEBUG` にしない。** MyBatis は Mapper インターフェースと同名のロガーに、実行 SQL と**バインドしたパラメータの実値**を DEBUG で出力する。`INSERT INTO users ... Parameters: ..., {bcrypt}$2a$10$...` の形でハッシュがそのまま乗る。開発環境で一時的に上げるのは構わないが、`application.yaml` に恒久設定として書かないこと。
- **`UserResponse` にパスワード関連のフィールドを作らない。**

---

## 3. ユーザー登録

自己サインアップ。**メール検証は行わない。**

```
POST /api/users {email, password, displayName}
  → 201 Created（登録済みアドレスなら 409 Conflict）
```

`users` に1行 INSERT して終わり。トークンも確認メールも介在しない。

### メール検証を落とした理由

メール送信基盤は認証本体とは別種の作業（SMTP の用意、テンプレート、送信失敗時の扱い、開発時の確認環境）で、これを抱えたまま始めると認証が動くまでの距離が伸びる。**まず動くものを作り、必要になった段階で足す**方針を採った。

### 引き受けたリスク

**1. なりすまし登録とアドレス占拠。** 他人のメールアドレスで登録でき、本人はそのアドレスで登録できなくなる（`uk_users_email` の一意制約）。本来これがメール検証の存在理由。社内利用であれば誰がやったか追跡でき、管理者が `enabled=false` にするかアドレスを付け替えれば復旧できるため、運用でカバーできる範囲と判断した。**外部に公開する場合はこの判断が成立しないので、その時点でメール検証を入れること。**

**2. パスワードリセットが管理者運用になる。** 実務上いちばん効く。パスワードを忘れたユーザーは管理者に連絡し、再発行してもらうしかない。利用者が数十人規模なら回るが、規模が増えると管理者の手間が線形に増える。ここが痛くなったらメール検証・リセットを入れる潮時。

**3. メールアドレスの正しさが保証されない。** 打ち間違えたまま登録が通る。後から通知機能（予約リマインダー等）を足すとき、届かないアドレスが混ざった状態から始まることになる。

### 必須の代替対策 — ドメイン許可リスト

メール検証を落とす以上、**ドメイン許可リストは省略しない。** `@company.co.jp` 以外を弾くだけで、公開エンドポイントへの外部からの無差別登録はほぼ止まる。CAPTCHA を検討する前にこちらを入れること。

許可ドメインは `@ConfigurationProperties` で `application.yaml` から型付きで受ける。`config/package-info.java` が示す「`@Value` の散在を避ける」方針に沿う。

### 重複時に 409 を返してよい理由

登録エンドポイントで「そのアドレスは登録済み」と返すと、一般にはメールアドレスの在籍確認 API として使われうる。しかし**ドメイン許可リストにより同一組織のメンバーしか登録できない**ため、漏れる情報にほとんど価値がない。UX が明確になる利点の方が上回るので、素直に 409 を返す。

外部公開に切り替える際は、ここを「常に 202 を返し、分岐はメール本文で行う」方式に改める必要がある。

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

### ユーザー列挙の防止

「メールアドレスが存在しない」と「パスワードが違う」で応答を変えない。`UserDetailsService` が `UsernameNotFoundException` を投げても Spring Security は既定で `BadCredentialsException` に隠蔽する（`hideUserNotFoundExceptions` が既定 true）。**ここを自分で握りつぶさないこと。**

応答時間の差でも漏れるため、厳密にやるならユーザーが存在しない場合もダミーハッシュに対して照合を走らせる。

### `enabled` チェックの順序

`enabled=false`（退職者・管理者による停止）のアカウントは、既定のままだと**パスワード照合より先に弾かれる**。`DaoAuthenticationProvider` が `preAuthenticationChecks`（有効・ロック・期限）を先に走らせるため、パスワードが間違っていても「無効です」と返り、アドレスの存在が漏れる。

有効性チェックをパスワード照合の後ろに移す。

```java
provider.setPreAuthenticationChecks(userDetails -> { /* 何もしない */ });
provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
```

パスワードが合った相手は既にアドレスとパスワードの両方を知っているので、そこで「アカウントが無効です」と伝えても追加で漏れる情報はない。将来メール検証を入れる場合も、未検証チェックはこの `postAuthenticationChecks` 側に置く。

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

認証のためのテーブルは `users` 1本。メール検証を行わないためトークンテーブルは不要。

```sql
-- V1__create_users.sql
CREATE TABLE users (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  email         VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name  VARCHAR(100) NOT NULL,
  role          VARCHAR(20)  NOT NULL,   -- USER / ADMIN
  enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at    DATETIME(6)  NOT NULL,
  updated_at    DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email)
);
```

補足：

- **これが最初のマイグレーションになる。** 予約系は `V2` 以降。`reservations.user_id` から `users.id` へ外部キーを張るため、この順序である必要がある。
- **`enabled` による論理削除。** 退職者をレコード削除すると、その人の過去の予約の外部キーが壊れる。
- **ログイン失敗回数はテーブルに持たない。** 理由は 8 節を参照。
- **ORM は MyBatis なので、このスキーマと `User` モデルの整合はアプリが起動時に検証しない。** Hibernate の `ddl-auto: validate` にあたる仕組みが無く、ずれは該当の SQL が走った時点で初めて表面化する。`users` に列を足すときは、このファイル・`domain/User`・`resources/mapper/UserMapper.xml` の3点を同じコミットで揃えること。

### 将来メール検証を足す場合の移行

```sql
-- DEFAULT TRUE で追加すること
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;
```

**`DEFAULT FALSE` で追加すると既存ユーザー全員が未検証扱いになり、一斉にログインできなくなる。** `TRUE` で追加して既存を検証済みとみなし、以降の新規登録だけ `false` で作る。

---

## 7. パッケージ構成

既存のレイヤ構成に `security/` を1つ追加する。

```
com.example.spacereserve
├── domain/           User, Role(enum)
├── repository/       UserMapper（@Mapper インターフェース。SQL は resources/mapper/UserMapper.xml）
├── service/          UserService, AuthService
├── controller/       AuthController, UserController
├── dto/request/      LoginRequest, SignUpRequest, ChangePasswordRequest
├── dto/response/     UserResponse
└── security/         ★新規
    ├── SecurityConfig                    SecurityFilterChain, PasswordEncoder
    ├── AppUserDetails                    UserDetails 実装（userId を保持、Serializable）
    └── AppUserDetailsService             UserDetailsService 実装
```

`config/` に混ぜず分けるのは、これらが業務ロジックでも単なる設定でもなく、**Spring Security という特定フレームワークへの適合層**だから。独立させておけば、認証方式を JWT や OIDC に変えるときの変更範囲がこのパッケージにほぼ閉じる。

`config/package-info.java` は「Spring Security の設定などが増えたらここに置く」と書いているが、実際に作るファイルが5個になるため方針を変えた。当該 package-info の記述は実装時に調整すること。

---

## 8. エラー応答

Controller から先のエラー応答は RFC 9457 の Problem Details に統一する。**ただし認証・認可の失敗は例外の発生場所によって処理経路が分かれ、フィルタ層のものは Problem Details にならない**点に注意。

| 例外・事象 | 発生場所 | 処理 | ステータス | 応答の形 |
|---|---|---|---|---|
| `BadCredentialsException` | ログイン Controller 内 | `GlobalExceptionHandler` | 401 | Problem Details |
| `InternalAuthenticationServiceException` | 同上 | `GlobalExceptionHandler` | 500 | Problem Details |
| `TooManyAttemptsException`（自作） | ログイン Service 内 | `GlobalExceptionHandler` | 429 | Problem Details |
| `DuplicateEmailException`（自作） | 登録 Service 内 | `GlobalExceptionHandler` | 409 | Problem Details |
| `ForbiddenOperationException`（自作） | 各 Service の所有者チェック | `GlobalExceptionHandler` | 403 | Problem Details |
| 未認証で保護リソースへアクセス | `ExceptionTranslationFilter` | `HttpStatusEntryPoint` | 401 | **ボディ無し** |
| ロール不足（`/api/admin/**` 等） | 同上 | `AccessDeniedHandlerImpl` → `/error` | 403 | **Boot 既定形式** |
| CSRF トークン不正・欠落 | `CsrfFilter` | 同上 | 403 | **Boot 既定形式** |

**フィルタ層の 401/403 は `GlobalExceptionHandler` を通らない。** `@RestControllerAdvice` は DispatcherServlet 内部の例外しか拾えないが、保護リソースへの未認証アクセスはその手前のサーブレットフィルタで弾かれるため。

Spring Security の標準ハンドラに Problem Details を書き出すものは存在せず、揃えるには自前の `AuthenticationEntryPoint` / `AccessDeniedHandler` を書くしかない。**当面は書かず、形式が分かれることを受け入れる。** 表示文言はフロントが持つ方針であり、サーバから返す `detail` を UI に使わないなら、フィルタ層でボディを組み立てる価値が薄いため。実測値は次のとおり。

```
401  Content-Type 無し、ボディ空
403  {"timestamp":"...","status":403,"error":"Forbidden","path":"/api/admin/users"}
```

**引き受けたリスク。** フロントはエラー応答を4通り（`errors` 付き / `detail` 付き / Boot 既定形式 / ボディ無し）で扱うことになる。特に 401 はボディが無いため、ステータス以外の手掛かりが無い。CSRF 切れ（再送で回復する）とロール不足（回復しない）も 403 で同じ形になり区別できない。ここが実際に困り始めたら、自前ハンドラを入れて `code` のような機械可読キーを持たせる方向で見直す。

**所有者チェックには Spring の `AccessDeniedException` を使わず自作例外を投げる。** `AccessDeniedException` を `@RestControllerAdvice` で捕まえると、`ExceptionTranslationFilter` が持つ「未認証ユーザーなら 403 ではなく 401 を返してログインへ誘導する」という分岐を横取りしてしまい、未ログイン時の挙動が壊れる。層ごとに例外の種類を分けておけばこの衝突が起きない。

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

自己サインアップは公開エンドポイントなので、ボットに叩かれる前提が要る。**メール検証という関門が無い分、この2つが実質的な防御線になる。**

- **ドメイン許可リスト** — 3 節のとおり必須。これが最も効く。
- **IP 単位のレート制限** — 1 IP あたり 1時間に数件で十分。

---

## 10. パスワードの変更とリセット

### 変更（ログイン中）

**現在のパスワードを必ず要求する。** セッションを乗っ取られた場合にパスワードごと奪われるのを防ぐため。

変更成功後は **当該ユーザーの他のセッションを全て無効化する。** `SpringSessionBackedSessionRegistry` の `findByPrincipalName` で列挙して消せる。これは Spring Session + Redis 構成でこそ確実に効く処理で、インメモリセッションでは単一インスタンス内でしか効かない。

### リセット（パスワード忘れ）

**管理者による再発行のみ。** メール送信基盤が無いため、利用者による自己リセットは提供しない。

```
POST /api/admin/users/{id}/reset-password  → 一時パスワードを応答で返す
```

管理者が一時パスワードを口頭なり別経路なりで本人に伝える運用。再発行時は当該ユーザーの既存セッションを全て無効化し、初回ログイン時に変更を促す。

この運用が回らなくなったら、メール検証とセルフリセットを入れる（「将来の検討事項」を参照）。

### 同時ログイン数の制御（任意）

`SpringSessionBackedSessionRegistry` を `sessionManagement().maximumSessions(1)` と組めば「1ユーザー1セッション、新しいログインで古い方を無効化」が実現できる。Redis 化の副産物。必要になったら入れる。

---

## 11. 依存とインフラ

### Gradle

```kotlin
implementation("org.springframework.boot:spring-boot-starter-security")
implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
testImplementation("org.springframework.boot:spring-boot-starter-security-test")
testImplementation("org.springframework.boot:spring-boot-starter-session-data-redis-test")
```

いずれも Boot 4.1 の BOM で管理されているためバージョン指定は不要（BOM の実物で確認済み）。ORM に使う `mybatis-spring-boot-starter` はサードパーティのため BOM の外にあり、こちらだけはバージョンを明記する（`build.gradle.kts` を参照）。Boot 3 系では Redis セッションに `spring-boot-starter-data-redis` と `spring-session-data-redis` の2本が要ったが、4.1 では `spring-boot-starter-session-data-redis` 1本にまとまっている。`build.gradle.kts` の既存コメントが指摘しているとおり、Boot 4 では自動設定が技術ごとのモジュールに分かれているので、必ず starter を経由すること。

メール送信を行わないため `spring-boot-starter-mail` は入れない。`backend/src/main/resources/templates/` も用途が無い（サーバ側で HTML を組み立てないため）。`static/` は React のビルド成果物の置き場として使う（12 節）。

### Compose に追加するサービス

```yaml
  redis:
    image: redis:8.10
    command: ["redis-server", "--appendonly", "yes"]
    volumes:
      - redis-data:/data
    ports:
      - "16379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 20
```

`app` の `depends_on` に `redis: {condition: service_healthy}` を追加し、`volumes:` に `redis-data:` を足す。ホストへの公開は MySQL を 13306 にずらしているのと同じ方針。

### Testcontainers

**Testcontainers 2.0.5 に Redis 専用モジュールは存在しない**（BOM の収録モジュール一覧で確認済み。`redpanda` はあるが別物）。既存の `MySQLContainer` と同じノリで書こうとすると詰まる。コアの `GenericContainer` を使い、サービス名を明示する。

```java
@Bean
@ServiceConnection("redis")
GenericContainer<?> redisContainer() {
    return new GenericContainer<>(DockerImageName.parse("redis:8.10")).withExposedPorts(6379);
}
```

`@ServiceConnection` に `"redis"` を渡すのは、`GenericContainer` からは接続の種類を推論できないため（`MySQLContainer` は型から自明なので引数不要）。追加依存は不要でコアの `testcontainers` に含まれる。

**この配線は実機で確認済み。** `LettuceConnectionFactory` の解決先がコンテナのマッピングポートと一致し、`PING` が通ること、セッションの実装が `RedisSessionRepository` になることを確認した。

テストが `@SpringBootTest` のたびに MySQL と Redis の2コンテナを起動するようになるため、実行時間は延びる。気になったら Testcontainers の再利用（`.withReuse(true)`）を検討する。

---

## 12. フロントエンド（React）との接続

**同一オリジンで配信する。** 別オリジン + CORS は採らない。

セッション Cookie を選んだ時点で、オリジンを分ける代償は JWT 構成より高くつく。CORS 設定、`SameSite=None` に伴う開発環境での HTTPS 必須化、preflight の扱いがすべて追加され、得られるものが無い。

### 配置

```
frontend/                       React + Vite（リポジトリ直下）
  └─ dist/  ─── ビルド ───→  backend/src/main/resources/static/
```

| | 開発 | 本番 |
|---|---|---|
| 配信 | Vite dev server `:5173` | Spring が `static/` から配信 |
| API | Vite の `server.proxy` で `/api` → `:8080` へ中継 | 同一ホストの `/api` |
| ブラウザから見たオリジン | 単一（`:5173`） | 単一 |

開発でも proxy を挟むことで**ブラウザから見たオリジンが常に1つになる**のが要点。これにより CORS 設定は開発・本番のどちらでも不要になり、Cookie の `SameSite` は既定の `Lax` のままでよい。

```ts
// vite.config.ts
server: {
  proxy: { '/api': 'http://localhost:8080' }
}
```

### CSRF の設定

```java
http.csrf(CsrfConfigurer::spa);
```

`spa()` は Spring Security 7.1 で入った SPA 向けのプリセットで、中身は
`CookieCsrfTokenRepository.withHttpOnlyFalse()` と、`setCsrfRequestAttributeName(null)` を
施したリクエストハンドラの2点（実物のバイトコードで確認済み）。手で書くなら次と等価になる。

```java
// spa() が無い版（Spring Security 7.0 以前）
http.csrf(csrf -> {
    csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse());
    // Spring Security 6 以降の遅延読み込みを解除する
    var handler = new CsrfTokenRequestAttributeHandler();
    handler.setCsrfRequestAttributeName(null);
    csrf.csrfTokenRequestHandler(handler);
});
```

厳密には `spa()` の方が一段良い。ヘッダ経由のトークンは素の値で照合しつつ、
レスポンスに載せる側は `XorCsrfTokenRequestAttributeHandler` のままにするため、
BREACH 対策のマスクを捨てずに済む。上の手書き版は両方とも素の値になる。

**内訳の2つは、どちらを落としても成立しない。**

- **`withHttpOnlyFalse()`** — 既定では `HttpOnly` が付き、JS から Cookie を読めない。読めなければ `X-XSRF-TOKEN` ヘッダに載せ直せないので、CSRF の仕組みそのものが成立しない。
- **遅延読み込みの解除** — Spring Security 6 以降、CSRF トークンは誰かがアクセスするまで解決されない。何もしないと `Set-Cookie: XSRF-TOKEN` が飛ばず、フロントは永遠にトークンを取得できない。`setCsrfRequestAttributeName(null)` で毎リクエスト解決に戻す。

「ログイン画面までは表示できるのに、ログインの POST だけが 403 になる」という形で表面化する。原因が CSRF だと気づきにくい。

### 静的リソースの認可設定

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/", "/index.html", "/favicon.ico", "/assets/**").permitAll()
    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
    .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/users").permitAll()
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .anyRequest().authenticated());
```

**SPA のシェル自体を `permitAll` にすること。** 全部を `authenticated()` にすると `GET /` が 401 を返し、ログイン画面にすら到達できない。「認証が必要かどうか」の判断はフロントが 401 応答を見て行う（後述）ので、HTML と JS の配信自体は保護しない。中身の保護は API 側で完結している。

**公開する API はメソッドまで絞る。** `/api/users` をパスだけで `permitAll` にすると、後から `GET /api/users`（利用者一覧）を生やした瞬間に無認証で読めるようになる。開けたいのは登録の POST だけなので、その時点で絞っておく。

`/actuator/health` を開けているのは、Compose のヘルスチェックと 1 節の「Redis のヘルスが載ることを確認する」ため。`/actuator/info` は開けない。

### ブラウザ向け既定挙動の無効化

Spring Security の既定はサーバサイドレンダリングの画面を前提にしているため、そのままだとフロントに HTML やリダイレクトが返る。REST として振る舞わせるために次を外す・差し替える。

| 対象 | 既定 | 本構成 |
|---|---|---|
| `formLogin` | `/login` の画面と POST 処理 | 無効化（ログインは 4 節の Controller） |
| `httpBasic` | `WWW-Authenticate` でブラウザのダイアログ | 無効化 |
| `requestCache` | 401 になった元リクエストをセッションに保存 | 無効化（ログイン後に元 URL へ戻す動線が無い） |
| `AuthenticationEntryPoint` | ログイン画面へ 302 | ステータスのみ返す（最終的には 8 節の Problem Details 版） |
| ログアウト成功 | `/login?logout` へ 302 | 204 |

`requestCache` を切るのは、使わない `SavedRequest` をセッションに積まないため。Redis に載る以上、不要なものは置かない。

### SPA フォールバック

クライアントサイドルーティングを使うため、`/reservations/42` を直接開いたりリロードしたりしたときに `index.html` を返す設定が要る。

**`/api/**` をフォールバックの対象に含めないこと。** 含めると存在しない API パスが 404 ではなく `index.html` を返し、フロントは JSON を期待して HTML を受け取る。「なぜか JSON パースエラーになる」という追いにくい壊れ方をする。

### フロント側の約束

- **HTTP クライアントは `axios` を使う。** `XSRF-TOKEN` Cookie を読んで `X-XSRF-TOKEN` ヘッダに載せる処理を既定で持っている。`fetch` だと毎回自前で書くことになる。同一オリジンなので Cookie の送信も既定のままでよい。
- **401 を受けたらログイン画面へ遷移するインターセプタを1本置く。** `HttpStatusEntryPoint`（8 節）により、未認証時はリダイレクトではなく 401 が返る。SPA としてはこれが正しい挙動なので、遷移の判断はフロント側の責務になる。
- **エラー表示の文言はフロントが持つ。** サーバの `detail` は開発者向けの説明とみなし、そのまま画面に出さない。i18n と文言調整をサーバのデプロイから切り離すため。
- **Controller 由来のエラーは Problem Details の形（`title` / `detail` / `errors`）を前提にしてよい。** ただし 8 節のとおり、フィルタ層で出る 401 はボディが無く、403 は Boot 既定形式になる。この2つだけは別扱いが要る。

### ビルドの統合

React のビルド成果物を `static/` へ入れる方法（Gradle から `npm run build` を叩くか、CI で成果物を配置するか）は**認証の設計とは独立**なので、ここでは決めない。開発中は Vite dev server を使うため、この統合が無くても支障は無い。

---

## 13. 実装順

1. 依存追加（security / session-data-redis）+ `SecurityConfig`（全エンドポイントを一旦保護して動作確認）
2. `V1__create_users.sql` + `User` + `Role` + `UserMapper`（インターフェース + XML）
3. `AppUserDetails` + `AppUserDetailsService`（**`enabled` チェック順の入れ替え込み**）
4. `/api/auth/login` `/logout` `/me` + Spring Session / Redis の疎通確認
5. ~~401/403 の Problem Details 化~~（見送り。理由は 8 節）
6. ユーザー登録 + ドメイン許可リスト
7. レート制限（Redis）
8. パスワード変更 + 管理者によるパスワード再発行
9. CSRF 設定 + 静的リソースの `permitAll` + Vite proxy 越しのログイン疎通（**12 節の2つの設定を両方入れないと通らない**）
10. ロールベースの認可、その後で予約側の所有者チェック
11. SPA フォールバックと React ビルドの `static/` への配置（本番構成の確認）

各段階で `@WithMockUser` を使ったテストを足していけば、Testcontainers の MySQL に対して実際のマイグレーションごと検証できる。Mapper 単体は `@MybatisTest` でも試せるが、**`UserMapper.xml` の SELECT 句とスキーマのずれは起動時に検出されない**ため、実際にクエリを走らせるテストを1本は置くこと。

---

## 将来の検討事項

いずれも初期スコープ外。必要になった時点で判断する。

- **メール検証とセルフパスワードリセット** — 最有力。管理者によるパスワード再発行の手間が無視できなくなったとき、あるいは外部公開に踏み切るときが導入時期。`spring-boot-starter-mail` + 開発用の mailpit、`user_tokens` テーブル（`purpose` で検証用とリセット用を相乗り）、`users.email_verified` の追加（**必ず `DEFAULT TRUE`**、6 節参照）がセットで必要になる。
- **漏洩パスワード照合**（Have I Been Pwned Range API）— ポリシー強化として最も費用対効果が高い。
- **多要素認証（TOTP）** — 自前パスワード管理を続けるなら、いずれ必要になる。
- **外部 IdP（OIDC）への移行** — 運用が重くなったら。`security/` パッケージを分離してあるのは、この移行の変更範囲を閉じ込めるため。
- **セッションの JSON シリアライズ化** — 運用に乗せる前に対応する。
- **同時ログイン数の制御** — 不正利用の兆候が出たら。
