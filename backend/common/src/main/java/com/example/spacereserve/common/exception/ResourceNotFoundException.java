package com.example.spacereserve.common.exception;

/**
 * 指定された識別子のリソースが存在しないことを表す。404 に翻訳される。
 */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String message) {
		super(message);
	}

	/**
	 * 「Reservation id=42 は存在しない」という定型のメッセージを組み立てる。
	 */
	public static ResourceNotFoundException of(String resourceName, Object id) {
		return new ResourceNotFoundException("%s not found: id=%s".formatted(resourceName, id));
	}

}
