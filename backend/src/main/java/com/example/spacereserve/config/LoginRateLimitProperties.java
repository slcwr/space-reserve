package com.example.spacereserve.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ログイン試行のレート制限（authentication.md 9 節）。
 *
 * @param threshold この回数まで許し、超えた分を拒否する。アカウントロックではないので、閾値は
 * 「打ち間違いを咎めない程度に緩く、総当たりには意味がない程度に厳しく」でよい。
 * @param window 最後の失敗からこの時間が経つと数え直す。
 */
@ConfigurationProperties("app.login.rate-limit")
public record LoginRateLimitProperties(int threshold, Duration window) {

}
