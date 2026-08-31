package com.example.spacereserve.exception;

/**
 * 試行回数が閾値を超えたことを表す。HTTP では 429 に対応する（authentication.md 8 節）。
 *
 * `AuthenticationException` を継承しないこと。継承すると GlobalExceptionHandler の認証失敗ハンドラに 拾われて 401
 * になり、「認証に失敗した」と「試行しすぎた」の区別が応答から消える。
 */
public class TooManyAttemptsException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public TooManyAttemptsException(String message) {
		super(message);
	}

}
