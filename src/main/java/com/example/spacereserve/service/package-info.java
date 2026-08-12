/**
 * 業務ルールとトランザクション境界を担う層。
 *
 * <p>{@code @Transactional} はこの層に付ける。Controller に付けると HTTP の都合と
 * トランザクションの範囲が癒着し、Repository に付けると複数リポジトリをまたぐ操作を
 * ひとつのトランザクションに収められない。参照のみのメソッドは
 * {@code @Transactional(readOnly = true)} にする。
 *
 * <p>DTO からエンティティへの変換もこの層で行う。Controller は DTO しか知らず、
 * Repository はエンティティしか知らない、という境界を保つため。
 *
 * <p>見つからない・状態が不正といった業務的な失敗は {@code exception} の例外を投げ、
 * HTTP ステータスへの変換は {@code GlobalExceptionHandler} に任せる。Service が
 * {@code ResponseEntity} や {@code HttpStatus} を知る必要はない。
 */
package com.example.spacereserve.service;
