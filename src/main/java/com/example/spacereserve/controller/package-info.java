/**
 * HTTP の境界を担う層。
 *
 * <p>受け持つのはリクエストの受け取り、Service の呼び出し、レスポンスの組み立てまで。
 * 業務ルール（予約が重複していないか、キャンセル可能な期限内かなど）はここに書かず
 * {@code service} に置く。Controller が肥大化してきたら、それは Service に移すべき
 * ロジックが漏れ出しているサイン。
 *
 * <p>エンティティを直接受け取らない・返さないこと。入力は {@code dto.request}、
 * 出力は {@code dto.response} を通す。エンティティをそのまま返すと、JPA の遅延ロードが
 * JSON 変換時に例外を投げたり、外部に見せたくないフィールドが露出したりする。
 *
 * <p>クラス名は {@code XxxController}、REST なので {@code @RestController} を付ける。
 */
package com.example.spacereserve.controller;
