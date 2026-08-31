package com.example.spacereserve.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * 業務例外
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ResourceNotFoundException.class)
	ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Resource not found");
		return problem;
	}

	/**
	 * {@code @Valid} の違反を、項目名をキーにしたマップとして返す。 既定の応答は違反内容が文章に埋もれるため、クライアントが項目単位で扱えるようにしている。
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleValidationFailure(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			// 同一項目に複数の違反がある場合は最初の1件だけ残す。
			errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}

		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		problem.setTitle("Validation failed");
		problem.setDetail("リクエストの内容に誤りがあります。");
		problem.setProperty("errors", errors);
		return problem;
	}

	/**
	 * どのアカウントが制限中かを漏らさないよう、文言は一般的なものに留める。残り時間も返さない （authentication.md 9 節）。
	 */
	@ExceptionHandler(TooManyAttemptsException.class)
	ProblemDetail handleTooManyAttempts(TooManyAttemptsException ex) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
		problem.setTitle("Too many attempts");
		problem.setDetail("試行回数が上限に達しました。しばらく時間をおいて再度お試しください。");
		return problem;
	}

	@ExceptionHandler(AuthenticationException.class)
	ProblemDetail handleAuthenticationFailure(AuthenticationException ex) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
		problem.setTitle("Authentication failed");
		problem.setDetail("メールアドレスまたはパスワードが違います。");
		return problem;
	}

	@ExceptionHandler(InternalAuthenticationServiceException.class)
	ProblemDetail handleAuthenticationServiceFailure(InternalAuthenticationServiceException ex) {
		logger.error("認証処理に失敗しました", ex);
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		problem.setTitle("Internal server error");
		problem.setDetail("処理に失敗しました。時間をおいて再度お試しください。");
		return problem;
	}

}
