/**
 * リクエストボディ。クライアントから受け取る形。
 *
 * 命名は XxxCreateRequest / XxxUpdateRequest。 Bean Validation のアノテーション（NotNull, Future
 * など）はここに書き、 Controller 側の引数に Valid を付けて発火させる。違反時の 400 応答は GlobalExceptionHandler
 * が項目別に整形する。
 *
 * ID や作成日時のようにサーバが決める値は含めない。含めると、クライアントから 上書きできてしまう。
 */
package com.example.spacereserve.dto.request;
