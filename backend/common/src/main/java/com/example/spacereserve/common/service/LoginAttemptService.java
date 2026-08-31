package com.example.spacereserve.common.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import com.example.spacereserve.common.config.LoginRateLimitProperties;
import com.example.spacereserve.common.exception.TooManyAttemptsException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * ログインの総当たり対策（authentication.md 9 節）。
 *
 * **アカウントロックは採らない。** 攻撃者が他人のアドレスで失敗を繰り返せばその人を締め出せてしまい、 認証の穴が可用性の穴に化ける。代わりに「メールアドレス + 送信元
 * IP」のペアで数え、そのペアだけを一時的に拒否する。 巻き添えを食うのは攻撃者自身の IP に限られる。
 *
 * 失敗回数を `users` に持たせないのは、ログイン失敗のたびに DB 書き込みが走るうえ、 本質的に一時データをマスタに混ぜることになるため。
 */
@Service
public class LoginAttemptService {

	/**
	 * セッションと同じ名前空間の下に置く。Redis を用途で共用する以上、キーの衝突を防ぐのは 名前空間の側の責務（authentication.md 1 節）。
	 */
	private static final String KEY_PREFIX = "space-reserve:login:fail:";

	private static final Logger logger = LoggerFactory.getLogger(LoginAttemptService.class);

	private final StringRedisTemplate redis;

	private final LoginRateLimitProperties properties;

	LoginAttemptService(StringRedisTemplate redis, LoginRateLimitProperties properties) {
		this.redis = redis;
		this.properties = properties;
	}

	/**
	 * 閾値を超えていれば拒否する。認証を試みる前に呼ぶこと。後に回すと、拒否すべき試行でも パスワード照合（BCrypt）が走り、計算コストを攻撃者に明け渡すことになる。
	 */
	public void verifyNotBlocked(String email, String clientIp) {
		String count = this.redis.opsForValue().get(key(email, clientIp));
		if (count != null && Integer.parseInt(count) >= this.properties.threshold()) {
			throw new TooManyAttemptsException("login attempts exceeded for " + email + " from " + clientIp);
		}
	}

	/**
	 * 失敗を1件数える。TTL は毎回入れ直す（最後の失敗からの経過で測る）。 初回だけ設定する書き方だと、閾値ぶんの試行を窓ごとに繰り返せてしまう。
	 *
	 * 鍵は IP を含むので、これで締め出されるのは攻撃元の IP に限られる。 正規の利用者が別の IP から入る経路は残る。
	 */
	public void recordFailure(String email, String clientIp) {
		String key = key(email, clientIp);
		Long count = this.redis.opsForValue().increment(key);
		this.redis.expire(key, this.properties.window());
		logger.warn("ログインに失敗しました email={} ip={} count={}", email, clientIp, count);
	}

	/**
	 * 認証に成功したら数え直す。成功したのに前の失敗が残っていると、 打ち間違いの多い利用者が正しく入れた直後に締め出される。
	 */
	public void reset(String email, String clientIp) {
		this.redis.delete(key(email, clientIp));
	}

	/**
	 * メールアドレスはハッシュにして載せる。Redis に利用者のアドレスを平文で置かないため。
	 *
	 * ハッシュ前に小文字化すること。`users.email` の照合順序 `utf8mb4_0900_ai_ci` は大文字小文字を 区別せず `TARO@` でも
	 * `taro@` でもログインできるため、正規化しないと大文字を混ぜるだけで 別の鍵になり、制限をすり抜けられる。
	 */
	private String key(String email, String clientIp) {
		return KEY_PREFIX + sha256(email.toLowerCase(Locale.ROOT)) + ":" + clientIp;
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 が利用できません", ex);
		}
	}

}
