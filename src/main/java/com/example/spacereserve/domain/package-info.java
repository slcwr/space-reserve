/**
 * ドメインモデル。JPA エンティティと、それに属する列挙・値オブジェクトを置く。
 *
 * <p>MVC の「M」にあたるが、DB のテーブル定義をそのまま写した器にしないこと。
 * 「この予約はキャンセルできるか」のように、エンティティ自身のデータだけで判断できる
 * ルールはエンティティのメソッドとして持たせる。複数のエンティティや外部の状態が
 * 必要になる判断は {@code service} 側に置く。
 *
 * <p>スキーマは Hibernate に生成させない（{@code ddl-auto: validate}）。
 * {@code src/main/resources/db/migration} 側を正とする運用を確立すること。
 */
package com.example.spacereserve.domain;
