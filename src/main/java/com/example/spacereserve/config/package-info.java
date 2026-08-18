/**
 * Configuration クラス置き場。
 *
 * CORS、Jackson のカスタマイズなどが増えたらここに置く。設定値そのものは application.yaml に書き、ConfigurationProperties
 * で型付きに受けるのを基本とする （Value 注釈の散在を避けるため）。
 *
 * Spring Security の設定は security パッケージに分ける。 詳細は docs/design/authentication.md の 7 節を参照。
 *
 * 現時点では既定の挙動で足りているため空。
 */
package com.example.spacereserve.config;
