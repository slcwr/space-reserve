package com.example.spacereserve.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 業務例外を RFC 9457 の Problem Details に翻訳する。
 *
 * <p>個々の Controller で try-catch を書かずに済ませるための集約点。
 * ここで捕まえていない例外は Spring の既定処理により 500 になる。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Resource not found");
		return problem;
	}

	/**
	 * {@code @Valid} の違反を、項目名をキーにしたマップとして返す。
	 * 既定の応答は違反内容が文章に埋もれるため、クライアントが項目単位で扱えるようにしている。
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

}
