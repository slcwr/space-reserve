/**
 * user / admin の両アプリが共有するモジュール。
 *
 * <p>
 * 実行可能 jar は作らず、ライブラリとして両アプリに取り込まれる（common/build.gradle.kts で bootJar を無効にしている）。
 *
 * <p>
 * 置くもの: ドメインモデル、Mapper、業務例外と HTTP への変換、認証の部品、 スキーマのマイグレーション（resources/db/migration）。
 *
 * <p>
 * 置かないもの: 特定のアプリにしか意味の無いもの。判断基準は「両方のアプリが要るか」で、 「複数箇所から呼ばれているか」ではない。後者で判断すると、user
 * の都合で書いたものが admin にも黙って公開され、共有モジュールが片方の事情で歪む。
 *
 * <p>
 * ここから user / admin のパッケージを参照しないこと。依存の向きは一方向で、Gradle の 依存宣言（user → common、admin →
 * common）がこれを強制する。
 */
package com.example.spacereserve.common;
