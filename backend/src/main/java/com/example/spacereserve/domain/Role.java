package com.example.spacereserve.domain;

/**
 * ユーザーのロール。`users.role` に定数名がそのまま VARCHAR で入る（authentication.md 6 節）。
 *
 * MyBatis の組み込み EnumTypeHandler が列の文字列と定数名を相互変換するため、設定も TypeHandler
 * も要らない。定数名を変えると既存行が読めなくなるので、 リネームはマイグレーションとセットで行うこと。
 *
 * Spring Security の `ROLE_` 接頭辞はここに持ち込まない。あれは権限名の表記規約であって ドメインの語彙ではないので、変換は security
 * パッケージ（AppUserDetails）で行う。
 */
public enum Role {

	USER, ADMIN

}
