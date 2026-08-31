package com.example.spacereserve.user.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * ログインのリクエストボディ。
 */
public record LoginRequest(@NotBlank(message = "メールアドレスは必須です") String email,
		@NotBlank(message = "パスワードは必須です") String password) {

	/**
	 * パスワードを出力しない。 バリデーション失敗時に Spring がリクエストオブジェクトをログへ出す経路があり、record の既定の toString
	 * はそこへ平文を載せてしまう（authentication.md 2 節）。
	 */
	@Override
	public String toString() {
		return "LoginRequest[email=" + this.email + "]";
	}

}
