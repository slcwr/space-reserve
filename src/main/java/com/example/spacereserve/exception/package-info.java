/**
 * 業務例外と、それを HTTP 応答へ変換する集約ハンドラ。
 *
 * <p>例外の送出元（Service）と HTTP への翻訳（{@code GlobalExceptionHandler}）を
 * 分けることで、Service を HTTP から独立させている。
 *
 * <p>応答形式は RFC 9457 の Problem Details（{@code application/problem+json}）に揃える。
 * Spring が標準で用意している {@code ProblemDetail} を使うため、独自のエラー DTO は作らない。
 */
package com.example.spacereserve.exception;
