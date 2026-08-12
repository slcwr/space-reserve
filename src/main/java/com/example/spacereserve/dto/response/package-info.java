/**
 * レスポンスボディ。REST 構成におけるビューにあたる。
 *
 * <p>命名は {@code XxxResponse}。エンティティからの変換は
 * {@code static XxxResponse from(Xxx entity)} をこのクラス自身に持たせるのが手軽で、
 * 変換規則が増えてきたら専用の Mapper に切り出す。
 *
 * <p>公開したくない項目（内部 ID、他ユーザーの個人情報など）を含めないこと。
 * ここが実質的に API の公開契約になるので、フィールドの削除・改名は破壊的変更になる。
 */
package com.example.spacereserve.dto.response;
