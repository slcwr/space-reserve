/**
 * API の境界をまたぐデータ構造。
 *
 * ドメインモデルと分けるのは、API の形と DB の形を独立して変えられるようにするため。 カラムを1本足すたびに API のレスポンスが勝手に変わる、という事故を防ぐ。
 *
 * いずれも record で定義し、不変にする。入力は request、出力は response に置く。
 */
package com.example.spacereserve.user.dto;
