/**
 * レスポンスボディ。REST 構成におけるビューにあたる。
 *
 * 命名は XxxResponse。ドメインモデルからの変換は static XxxResponse from(Xxx model) をこのクラス自身に持たせるのが手軽で、
 * 変換規則が増えてきたら専用の変換クラス（XxxResponseAssembler など）に切り出す。 この変換クラスを「Mapper」と呼ばないこと。MyBatis の
 * Mapper と紛らわしくなる。
 *
 * 公開したくない項目（内部 ID、他ユーザーの個人情報など）を含めないこと。 ここが実質的に API の公開契約になるので、フィールドの削除・改名は破壊的変更になる。
 */
package com.example.spacereserve.dto.response;
