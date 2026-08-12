/**
 * {@code @Configuration} クラス置き場。
 *
 * <p>CORS、Jackson のカスタマイズ、Spring Security の設定などが増えたらここに置く。
 * 設定値そのものは {@code application.yaml} に書き、{@code @ConfigurationProperties} で
 * 型付きに受けるのを基本とする（{@code @Value} の散在を避けるため）。
 *
 * <p>現時点では既定の挙動で足りているため空。
 */
package com.example.spacereserve.config;
