/**
 * API の境界をまたぐデータ構造。
 *
 * <p>エンティティと分けるのは、API の形と DB の形を独立して変えられるようにするため。
 * カラムを1本足すたびに API のレスポンスが勝手に変わる、という事故を防ぐ。
 *
 * <p>いずれも {@code record} で定義し、不変にする。入力は {@code request}、
 * 出力は {@code response} に置く。
 *
 * @see com.example.spacereserve.dto.request
 * @see com.example.spacereserve.dto.response
 */
package com.example.spacereserve.dto;
