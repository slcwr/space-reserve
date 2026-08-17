/**
 * HTTP の境界を担う層。
 *
 * 受け持つのはリクエストの受け取り、Service の呼び出し、レスポンスの組み立てまで。
 * 業務ルール（予約が重複していないか、キャンセル可能な期限内かなど）はここに書かず
 * service に置く。Controller が肥大化してきたら、それは Service に移すべきロジックが
 * 漏れ出しているサイン。
 *
 * ドメインモデルを直接受け取らない・返さないこと。入力は dto.request、
 * 出力は dto.response を通す。API の形と DB の形を独立して変えられるようにするためで、
 * これを崩すとカラムを1本足しただけで API のレスポンスが変わる。外部に見せたくない
 * フィールド（passwordHash など）の露出を防ぐ意味もある。
 *
 * クラス名は XxxController、REST なので RestController 注釈を付ける。
 */
package com.example.spacereserve.controller;
