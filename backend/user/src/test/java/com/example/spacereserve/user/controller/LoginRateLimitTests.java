package com.example.spacereserve.user.controller;

import java.util.Locale;
import java.util.Set;

import jakarta.servlet.http.Cookie;

import com.example.spacereserve.common.testsupport.TestcontainersConfiguration;
import com.example.spacereserve.common.config.LoginRateLimitProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ログインの総当たり対策（authentication.md 9 節）。
 *
 * 閾値は設定から読む。テスト側で小さい値に上書きすると `@SpringBootTest` のコンテキストが別物になり、 Testcontainers が MySQL と
 * Redis をもう1組立ち上げることになるため、実際の設定値のまま回す。
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class LoginRateLimitTests {

	private static final String EMAIL = "taro@example.com";

	private static final String PASSWORD = "correct-horse-battery-staple";

	private static final String WRONG_PASSWORD = "wrong-password";

	private static final String FAIL_KEY_PATTERN = "space-reserve:login:fail:*";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private StringRedisTemplate redis;

	@Autowired
	private LoginRateLimitProperties properties;

	private Cookie csrfToken;

	@BeforeEach
	void setUp() throws Exception {
		this.jdbcTemplate.update("DELETE FROM users");
		this.jdbcTemplate.update("""
				INSERT INTO users (email, password_hash, display_name, role, enabled, created_at, updated_at)
				VALUES (?, ?, '山田太郎', 'USER', TRUE, NOW(6), NOW(6))
				""", EMAIL, this.passwordEncoder.encode(PASSWORD));
		clearFailureCounts(this.redis);
		this.csrfToken = this.mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
	}

	/**
	 * 閾値を超えたら、**正しいパスワードでも**拒否されること。これが通るということは、判定が `authenticate()` より前に置かれている（＝拒否すべき試行で
	 * BCrypt を回していない）ことの裏付けになる。
	 */
	@Test
	void blocksAfterThresholdEvenWithCorrectPassword() throws Exception {
		for (int i = 0; i < this.properties.threshold(); i++) {
			login(EMAIL, WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}

		login(EMAIL, PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.title").value("Too many attempts"));
	}

	/** 閾値の手前までなら、正しいパスワードで入れて数え直されること。 */
	@Test
	void successResetsFailureCount() throws Exception {
		for (int i = 0; i < this.properties.threshold() - 1; i++) {
			login(EMAIL, WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}

		login(EMAIL, PASSWORD).andExpect(status().isOk());

		assertThat(this.redis.keys(FAIL_KEY_PATTERN)).isEmpty();
	}

	/**
	 * `users.email` の照合順序 `utf8mb4_0900_ai_ci` は大文字小文字を区別しないため、正規化しないと
	 * 大文字を混ぜるだけで別の鍵になり制限をすり抜けられる。
	 */
	@Test
	void countsCaseInsensitively() throws Exception {
		login(EMAIL.toUpperCase(Locale.ROOT), WRONG_PASSWORD).andExpect(status().isUnauthorized());
		login(EMAIL, WRONG_PASSWORD).andExpect(status().isUnauthorized());

		assertThat(this.redis.keys(FAIL_KEY_PATTERN)).hasSize(1);
	}

	/**
	 * TTL が入っていること。`EXPIRE` を落とすとカウンタが永久に残り、「一時拒否」のつもりが
	 * 恒久ロックになる。しかも失敗するのは閾値に達した利用者だけなので、気づくのが遅れる。
	 */
	@Test
	void failureCountExpires() throws Exception {
		login(EMAIL, WRONG_PASSWORD).andExpect(status().isUnauthorized());

		String key = this.redis.keys(FAIL_KEY_PATTERN).iterator().next();
		assertThat(this.redis.getExpire(key)).isPositive().isLessThanOrEqualTo(this.properties.window().toSeconds());
	}

	/** 鍵にメールアドレスを平文で載せないこと。 */
	@Test
	void doesNotStoreRawEmailInRedis() throws Exception {
		login(EMAIL, WRONG_PASSWORD).andExpect(status().isUnauthorized());

		assertThat(this.redis.keys(FAIL_KEY_PATTERN)).isNotEmpty()
			.allSatisfy((key) -> assertThat(key).doesNotContain(EMAIL));
	}

	private ResultActions login(String email, String password) throws Exception {
		return this.mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password))
			.cookie(this.csrfToken)
			.header("X-XSRF-TOKEN", this.csrfToken.getValue()));
	}

	/**
	 * ログインの応答が Redis に残る失敗カウントに依存するようになったため、テスト間で持ち越さない。 これを怠ると、先に走ったテストの失敗ぶんで後のテストが 429
	 * になる。
	 */
	static void clearFailureCounts(StringRedisTemplate redis) {
		Set<String> keys = redis.keys(FAIL_KEY_PATTERN);
		if (!keys.isEmpty()) {
			redis.delete(keys);
		}
	}

}
