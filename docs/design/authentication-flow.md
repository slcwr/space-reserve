# 認証・認可 フロー図

`authentication.md` で決めた方針が、実行時に Spring Security のどの部品を通るのかを図にしたもの。
判断の根拠は書かない（そちらは `authentication.md`）。ここは **どこで何が起きるか** だけを扱う。

---

## 1. 全体構成

```mermaid
flowchart LR
    subgraph client["ブラウザ / フロント"]
        B["SESSION Cookie<br/>XSRF-TOKEN Cookie"]
    end

    subgraph app["Spring Boot アプリ"]
        SRF["SessionRepositoryFilter<br/>(Spring Session)"]
        SEC["springSecurityFilterChain<br/>(フィルタ群)"]
        DS["DispatcherServlet"]
        CTL["Controller"]
        SVC["Service<br/>所有者チェック"]
        REPO["Mapper<br/>(MyBatis)"]
    end

    RDS[("Redis<br/>セッション / レート制限")]
    DB[("MySQL<br/>users, reservations")]

    B -->|HTTP| SRF --> SEC --> DS --> CTL --> SVC --> REPO
    SRF <-->|セッション読み書き| RDS
    SEC -->|認証情報の復元| RDS
    SVC -->|試行回数 INCR| RDS
    REPO --> DB
```

要点は2つ。

- **`SessionRepositoryFilter` は Security のフィルタ群より手前にいる。** Spring Session が `HttpServletRequest` を差し替えるので、以降の `getSession()` は全て Redis を向く。Security 側は自分が Redis を見ていることを知らない。
- **Redis は3用途を兼ねる**（セッション / ログイン失敗カウント / 登録レート制限）。だから `namespace` を明示する。

---

## 2. フィルタチェーンの並び

`formLogin` を使わないので `UsernamePasswordAuthenticationFilter` は**いない**。ログインは通常の Controller。

```mermaid
flowchart TD
    REQ([リクエスト]) --> F1["SessionRepositoryFilter<br/>request を Redis 対応に差し替え"]
    F1 --> F2["SecurityContextHolderFilter<br/>セッションから SecurityContext を復元"]
    F2 --> F3["HeaderWriterFilter / CorsFilter"]
    F3 --> F4["CsrfFilter<br/>POST/PUT/DELETE のトークン照合"]
    F4 --> F5["LogoutFilter<br/>/logout を処理"]
    F5 --> F6["AnonymousAuthenticationFilter<br/>未認証なら anonymous を詰める"]
    F6 --> F7["ExceptionTranslationFilter<br/>下流の例外を待ち受ける"]
    F7 --> F8["AuthorizationFilter<br/>URL 単位の認可判定"]
    F8 --> DS["DispatcherServlet → Controller"]

    F4 -.->|トークン不一致| E403["403 AccessDeniedHandler"]
    F8 -.->|認可 NG| F7
    F7 -.->|未認証| E401["401 AuthenticationEntryPoint"]
    F7 -.->|認証済みだが権限不足| E403

    style E401 fill:#7f1d1d,color:#fff
    style E403 fill:#7f1d1d,color:#fff
```

**`ExceptionTranslationFilter` が `AuthorizationFilter` より上流にいる**のがポイント。下流で投げられた `AccessDeniedException` をここで受け止め、「未認証なら 401、認証済みなら 403」に振り分ける。この分岐が 8 節の「所有者チェックに Spring の `AccessDeniedException` を使わない」理由。

---

## 3. ログイン

```mermaid
sequenceDiagram
    autonumber
    participant C as クライアント
    participant Ctl as AuthController
    participant RL as レート制限<br/>(Redis)
    participant AM as AuthenticationManager
    participant DAO as DaoAuthenticationProvider
    participant UDS as AppUserDetailsService
    participant DB as MySQL
    participant Enc as PasswordEncoder
    participant Repo as SecurityContextRepository
    participant RS as Redis (session)

    C->>Ctl: POST /api/auth/login {email, password}
    Ctl->>RL: INCR login:fail:{emailHash}:{ip} を確認
    alt 閾値超過
        RL-->>Ctl: 制限中
        Ctl-->>C: 429 Problem Details
    else 通過
        Ctl->>AM: authenticate(unauthenticated token)
        AM->>DAO: 委譲
        Note over DAO: preAuthenticationChecks は<br/>空実装に差し替え済み
        DAO->>UDS: loadUserByUsername(email)
        UDS->>DB: SELECT * FROM users WHERE email = ?
        alt ユーザーなし
            DB-->>UDS: 0 件
            UDS-->>DAO: UsernameNotFoundException
            Note over DAO: hideUserNotFoundExceptions=true
            DAO-->>Ctl: BadCredentialsException
        else ユーザーあり
            DB-->>UDS: User 行
            UDS-->>DAO: AppUserDetails
            DAO->>Enc: matches(raw, {bcrypt}hash)
            alt 不一致
                Enc-->>DAO: false
                DAO-->>Ctl: BadCredentialsException
            else 一致
                Note over DAO: ここで初めて postAuthenticationChecks<br/>= enabled / locked を判定
                DAO-->>Ctl: Authentication (認証済み)
            end
        end
        alt 認証成功
            Ctl->>Ctl: changeSessionId()<br/>セッション固定攻撃対策
            Ctl->>Repo: saveContext(context, req, res)
            Repo->>RS: SecurityContext をシリアライズして保存
            Ctl->>RL: 失敗カウントを削除
            Ctl-->>C: 200 UserResponse + Set-Cookie: SESSION
        else 失敗
            Ctl->>RL: INCR + EXPIRE 15m
            Ctl-->>C: 401 Problem Details（文言は共通）
        end
    end
```

赤線を引くところ:

- **`saveContext` を自分で呼ぶ。** `formLogin` を使わない代償。忘れるとログインは 200 で返るのに、次のリクエストが 401 になる。
- **`enabled` の判定はパスワード照合の後。** 既定の順序のままだと、パスワードが違っても「無効なアカウント」と返ってアドレスの存在が漏れる。
- **失敗系の応答はすべて同じ形。** 「存在しない」と「パスワード違い」を区別しない。

---

## 4. 認証済みリクエスト（GET）

```mermaid
sequenceDiagram
    autonumber
    participant C as クライアント
    participant SRF as SessionRepositoryFilter
    participant RS as Redis
    participant SCF as SecurityContextHolderFilter
    participant AF as AuthorizationFilter
    participant Ctl as Controller
    participant Svc as Service
    participant DB as MySQL

    C->>SRF: GET /api/reservations/42<br/>Cookie: SESSION=xxx
    SRF->>RS: HGETALL space-reserve:session:xxx
    RS-->>SRF: セッション属性（SPRING_SECURITY_CONTEXT 含む）
    SRF->>SCF: 差し替え済み request
    SCF->>SCF: SecurityContextHolder に復元<br/>（Supplier 経由の遅延読み込み）
    SCF->>AF: 継続
    AF->>AF: authenticated() を満たすか
    AF->>Ctl: 通過
    Ctl->>Ctl: @AuthenticationPrincipal AppUserDetails
    Note over Ctl,Svc: Controller から Service へは userId (Long) だけ渡す
    Ctl->>Svc: get(reservationId, userId)
    Svc->>DB: SELECT ... WHERE id = 42
    DB-->>Svc: Reservation
    Svc->>Svc: 所有者チェック<br/>owner != userId && !ADMIN
    alt 他人の予約
        Svc-->>Ctl: ForbiddenOperationException（自作）
        Ctl-->>C: 403 Problem Details<br/>（GlobalExceptionHandler 経由）
    else 本人 or ADMIN
        Svc-->>Ctl: 結果
        Ctl-->>C: 200
    end
```

**所有者チェックだけが Service 層にある**のは、対象レコードを読まないと判定できないから。URL とロールだけで決まる制御（`/api/admin/**`）は `AuthorizationFilter` の側で終わる。

---

## 5. CSRF（更新系リクエスト）

```mermaid
sequenceDiagram
    autonumber
    participant C as クライアント
    participant CF as CsrfFilter
    participant Repo as CookieCsrfTokenRepository
    participant Ctl as Controller

    Note over C,Repo: 初回 GET
    C->>CF: GET /api/me
    CF->>Repo: トークン生成
    Repo-->>C: Set-Cookie: XSRF-TOKEN=abc（HttpOnly=false）

    Note over C,Ctl: 以降の更新系
    C->>CF: POST /api/reservations<br/>Cookie: SESSION, XSRF-TOKEN=abc<br/>Header: X-XSRF-TOKEN: abc
    CF->>Repo: Cookie 側の値を取得
    CF->>CF: ヘッダ値と比較
    alt 一致
        CF->>Ctl: 通過
        Ctl-->>C: 201
    else 不一致 / ヘッダ無し
        CF-->>C: 403 AccessDeniedHandler
    end
```

罠サイトはクロスオリジンのため **Cookie は自動送信できてもその値を JS から読んでヘッダに載せることができない**。この非対称性が防御の本体。したがってフロントは「Cookie を読んでヘッダに詰め直す」実装が必須になる。

---

## 6. 401 / 403 の2経路

同じステータスでも、**どこで発生したかで通る道が違う**。ここを揃えないと Problem Details に統一したはずの応答が崩れる。

```mermaid
flowchart TD
    START([認証・認可の失敗]) --> Q{発生場所は}

    Q -->|フィルタ層| FT["ExceptionTranslationFilter"]
    Q -->|Controller / Service| DSP["DispatcherServlet 内部"]

    FT --> Q2{認証済みか}
    Q2 -->|未認証| EP["ProblemAuthenticationEntryPoint<br/>→ 401"]
    Q2 -->|認証済み・権限不足| ADH["ProblemAccessDeniedHandler<br/>→ 403"]

    DSP --> GEH["@RestControllerAdvice<br/>GlobalExceptionHandler"]
    GEH --> R1["BadCredentialsException → 401"]
    GEH --> R2["TooManyAttemptsException → 429"]
    GEH --> R3["DuplicateEmailException → 409"]
    GEH --> R4["ForbiddenOperationException → 403"]

    EP --> OUT([RFC 9457 Problem Details])
    ADH --> OUT
    R1 --> OUT
    R2 --> OUT
    R3 --> OUT
    R4 --> OUT

    style FT fill:#78350f,color:#fff
    style GEH fill:#1e3a5f,color:#fff
```

**`@RestControllerAdvice` はフィルタ層に届かない。** 保護リソースへの未認証アクセスは DispatcherServlet の手前で弾かれるため、`ProblemAuthenticationEntryPoint` / `ProblemAccessDeniedHandler` を書かないと 401/403 だけ Spring 既定の形式で返る。

---

## 7. ユーザー登録

```mermaid
flowchart TD
    A([POST /api/users]) --> B{Bean Validation<br/>email 形式 / 12〜64 文字}
    B -->|NG| E400["400 errors マップ付き"]
    B -->|OK| C{IP レート制限<br/>Redis INCR}
    C -->|超過| E429["429"]
    C -->|OK| D{ドメイン許可リスト<br/>@company.co.jp か}
    D -->|不許可| E400b["400"]
    D -->|許可| F{email 重複<br/>uk_users_email}
    F -->|重複| E409["409 DuplicateEmailException"]
    F -->|新規| G["passwordEncoder.encode()<br/>→ {bcrypt}$2a$10$..."]
    G --> H["INSERT INTO users<br/>role=USER, enabled=true"]
    H --> I([201 Created])

    style E400 fill:#7f1d1d,color:#fff
    style E400b fill:#7f1d1d,color:#fff
    style E409 fill:#7f1d1d,color:#fff
    style E429 fill:#7f1d1d,color:#fff
```

メール検証の関門が無いぶん、**ドメイン許可リストと IP レート制限の2つが唯一の防御線**になる。

---

## 8. パスワード変更 — 他セッションの無効化

```mermaid
sequenceDiagram
    autonumber
    participant C as クライアント（PC）
    participant Ctl as UserController
    participant Svc as UserService
    participant Enc as PasswordEncoder
    participant DB as MySQL
    participant Reg as SpringSessionBackedSessionRegistry
    participant RS as Redis

    C->>Ctl: POST /api/me/password<br/>{currentPassword, newPassword}
    Ctl->>Svc: changePassword(userId, ...)
    Svc->>Enc: matches(current, hash)
    alt 現在のパスワードが不一致
        Svc-->>C: 401 / 400
    else 一致
        Svc->>Enc: encode(new)
        Svc->>DB: UPDATE users SET password_hash = ?
        Svc->>Reg: findByPrincipalName(email)
        Reg->>RS: 当該ユーザーの全セッションを列挙
        RS-->>Reg: session-a（PC）, session-b（スマホ）...
        Svc->>Reg: 現在のセッション以外を expireNow()
        Reg->>RS: DEL
        Svc-->>C: 204（今使っているセッションは維持）
    end
```

**現在のパスワードを必ず要求する**のは、セッションを乗っ取られたときにパスワードごと奪われるのを防ぐため。そして奪われていた場合に備え、変更後は他セッションを全て切る。この一括無効化はセッションが Redis に集約されているからこそ確実に効く。

管理者によるパスワード再発行（`POST /api/admin/users/{id}/reset-password`）も、最後のセッション無効化の部分は同じ処理を通る。

---

## 9. ログアウト

```mermaid
sequenceDiagram
    participant C as クライアント
    participant LF as LogoutFilter
    participant RS as Redis

    C->>LF: POST /logout（+ CSRF ヘッダ）
    LF->>RS: セッション削除
    LF->>LF: SecurityContextHolder.clearContext()
    LF-->>C: 204 + Cookie 失効
```

セッション方式を選んだ結果、**失効はここで即座に完了する**。JWT のようにブラックリストを持つ必要がない。これが 1 節で JWT を落とした理由そのもの。

---

## 参照

- 判断の根拠・却下した案・引き受けたリスク → [authentication.md](authentication.md)
- 実装順 → 同 12 節
