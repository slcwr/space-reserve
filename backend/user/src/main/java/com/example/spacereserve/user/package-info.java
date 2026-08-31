/**
 * 利用者向けアプリ。8080 で待ち受け、フロント（Vite の proxy 経由）からの `/api` を受ける。
 *
 * <p>
 * 共有部分は common モジュールにあり、ここには利用者向けの Controller / DTO / Service と
 * 認可ルール（security.UserSecurityConfig）だけを置く。
 *
 * <p>
 * admin モジュールを参照しないこと。両者は別プロセスで、依存関係にも入っていない。 共有が要るものは common へ上げる。
 *
 * <p>
 * Flyway によるマイグレーションはこのアプリだけが実行する（SQL は common にある）。 適用主体を1つに絞らないと、同時起動でロック待ちや競合が起きる。
 */
package com.example.spacereserve.user;
