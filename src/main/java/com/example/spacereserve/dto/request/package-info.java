/**
 * リクエストボディ。クライアントから受け取る形。
 *
 * <p>命名は {@code XxxCreateRequest} / {@code XxxUpdateRequest}。
 * Bean Validation のアノテーション（{@code @NotNull}, {@code @Future} など）はここに書き、
 * Controller 側の引数に {@code @Valid} を付けて発火させる。違反時の 400 応答は
 * {@code GlobalExceptionHandler} が項目別に整形する。
 *
 * <p>ID や作成日時のようにサーバが決める値は含めない。含めると、クライアントから
 * 上書きできてしまう。
 */
package com.example.spacereserve.dto.request;
