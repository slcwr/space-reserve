/**
 * 業務例外と、それを HTTP 応答へ変換する集約ハンドラ。
 *
 * 例外の送出元（Service）と HTTP への翻訳（GlobalExceptionHandler）を分けることで、
 * Service を HTTP から独立させている。
 *
 * 応答形式は RFC 9457 の Problem Details（application/problem+json）に揃える。
 * Spring が標準で用意している ProblemDetail を使うため、独自のエラー DTO は作らない。
 *
 * 認証・認可の失敗はこのハンドラを通らない経路がある。フィルタ層で弾かれる 401 / 403 は
 * security パッケージの EntryPoint / AccessDeniedHandler が受け持つ。
 * 詳細は docs/design/authentication.md の 8 節を参照。
 */
package com.example.spacereserve.exception;
