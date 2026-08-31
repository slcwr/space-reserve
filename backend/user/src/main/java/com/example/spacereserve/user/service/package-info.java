/**
 * 利用者向けアプリ固有の業務ロジック。
 *
 * <p>
 * 層としての約束（トランザクション境界、MyBatis のダーティチェックが無いこと、例外の投げ方など）は common.service の package-info
 * を参照。ここではそれに加えて「admin と共有するものは置かない」ことだけを守る。 共有が必要になったら common.service へ移す。
 *
 * <p>
 * 現時点では空。ログイン試行の制限（LoginAttemptService）は両アプリで動くため common にある。
 */
package com.example.spacereserve.user.service;
