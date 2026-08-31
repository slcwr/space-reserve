/**
 * 管理向けアプリ。user とは別プロセスで、8081 で待ち受ける。
 *
 * <p>
 * アプリ全体が ROLE_ADMIN を要求する（security.AdminSecurityConfig）。プロセスを分けている 実利はここにあり、URL
 * ごとの権限指定を書き漏らしても管理機能が利用者側に露出しない。
 *
 * <p>
 * user モジュールを参照しないこと。共有が必要なものは common へ上げる。
 *
 * <p>
 * <b>現状は骨組みだけで、動かせる状態ではない。</b> 管理機能そのものが未実装なのに加えて、 ログイン口が無いこと、AppUserDetails が権限を
 * ROLE_USER で固定していて ADMIN を表す手段が 無いことが未了になっている。詳細は AdminSecurityConfig の Javadoc を参照。
 */
package com.example.spacereserve.admin;
