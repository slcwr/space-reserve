package com.example.spacereserve.dto.response;

import com.example.spacereserve.domain.Role;
import com.example.spacereserve.security.AppUserDetails;

/**
 * ログイン中のユーザーを表すレスポンス。ログイン成功時と /me で返す。
 *
 * パスワード関連の項目は持たない（authentication.md 2 節）。ここが API の公開契約になるため、 項目の削除・改名は破壊的変更になる。
 *
 * role を載せているのは、フロントが管理者向けの導線を出し分けるため。認可の判断そのものは
 * サーバ側で行う。この値を信じて権限を決めるのはクライアントの表示までにとどめること。
 */
public record UserResponse(Long id, String email, String displayName, Role role) {

	/**
	 * 認証済みプリンシパルから組み立てる。ログイン直後は DB を読み直さずこれで返せる。
	 */
	public static UserResponse from(AppUserDetails principal) {
		return new UserResponse(principal.getUserId(), principal.getUsername(), principal.getDisplayName(),
				principal.getRole());
	}

}
