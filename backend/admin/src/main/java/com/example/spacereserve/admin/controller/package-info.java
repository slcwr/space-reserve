/**
 * 管理向け API の HTTP 境界。
 *
 * <p>
 * 層としての約束（業務ルールを書かない、ドメインモデルを直接やり取りしない、命名は XxxController）は user.controller の package-info
 * と同じ。
 *
 * <p>
 * URL に `/api/admin` の接頭辞は要らない。アプリ全体が管理者専用なので、パスで区別する必要が無い。
 *
 * <p>
 * 現時点では空。
 */
package com.example.spacereserve.admin.controller;
