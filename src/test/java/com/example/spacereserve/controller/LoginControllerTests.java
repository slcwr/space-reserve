package com.example.spacereserve.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.Cookie;

import com.example.spacereserve.TestcontainersConfiguration;

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
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ログインの HTTP レベルの挙動。Cookie を素通しで扱うため `SecurityMockMvcRequestPostProcessors.csrf()`
 * は使わない。あれはトークンをリポジトリ経由で注入するので、ここで確かめたい「Cookie とヘッダの往復が 実際に成立するか」を素通ししてしまう。
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class LoginControllerTests {

	private static final String EMAIL = "taro@example.com";

	private static final String PASSWORD = "correct-horse-battery-staple";

	private static final String DISPLAY_NAME = "山田太郎";

	private static final String LOGIN_BODY = """
			{"email": "taro@example.com", "password": "correct-horse-battery-staple"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private StringRedisTemplate redis;

	@BeforeEach
	void insertUser() {
		this.jdbcTemplate.update("DELETE FROM users");
		this.jdbcTemplate.update("""
				INSERT INTO users (email, password_hash, display_name, role, enabled, created_at, updated_at)
				VALUES (?, ?, ?, 'USER', TRUE, NOW(6), NOW(6))
				""", EMAIL, this.passwordEncoder.encode(PASSWORD), DISPLAY_NAME);
		// ログインの応答がレート制限の状態に依存するようになったため、他のテストの
		// 失敗ぶんを持ち越さない。残っていると正しい資格情報でも 429 になる。
		LoginRateLimitTests.clearFailureCounts(this.redis);
	}

	/**
	 * `CsrfFilter` は `AuthorizationFilter` より前段にいるため、401 で弾かれる場合でもトークンは発行される。
	 * これが崩れると、開発環境（`GET /` を Vite が返し Spring を通らない）で最初のログイン POST が 403 になる。 原因が CSRF
	 * だと気づきにくい壊れ方をするので、ここで固定しておく（authentication.md 12 節）。
	 */
	@Test
	void meIssuesCsrfTokenEvenWhenUnauthenticated() throws Exception {
		MvcResult result = this.mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized()).andReturn();

		Cookie token = result.getResponse().getCookie("XSRF-TOKEN");
		assertThat(token).isNotNull();
		assertThat(token.getValue()).isNotBlank();
		assertThat(token.isHttpOnly()).isFalse();
	}

	/**
	 * `CsrfAuthenticationStrategy` がログイン成功時にトークンを振り直すこと。据え置くと、攻撃者が事前に
	 * 固定したトークンが認証済みセッションに対してそのまま通る。
	 */
	@Test
	void loginRotatesCsrfToken() throws Exception {
		Cookie before = primeCsrfToken();

		MvcResult login = performLogin(before);

		Cookie after = browserCookies(login).get("XSRF-TOKEN");
		assertThat(after).isNotNull();
		assertThat(after.getValue()).isNotBlank().isNotEqualTo(before.getValue());
	}

	@Test
	void meReturnsCurrentUserAfterLogin() throws Exception {
		MvcResult login = performLogin(primeCsrfToken());

		this.mockMvc.perform(get("/api/auth/me").cookie(browserCookies(login).values().toArray(new Cookie[0])))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(EMAIL))
			.andExpect(jsonPath("$.displayName").value(DISPLAY_NAME))
			.andExpect(jsonPath("$.role").value("USER"));
	}

	private Cookie primeCsrfToken() throws Exception {
		return browserCookies(this.mockMvc.perform(get("/api/auth/me")).andReturn()).get("XSRF-TOKEN");
	}

	/**
	 * 応答の Cookie をブラウザと同じ解釈で畳む。同名は後勝ち、`Max-Age=0` は削除。
	 *
	 * `MockHttpServletResponse.getCookie()` は最初の一致を返すため、ここでは使えない。
	 * `CsrfAuthenticationStrategy` は旧トークンの削除（空値 + `Max-Age=0`）と新トークンの設定を
	 * 続けて書くので、素直に取ると削除側の空文字を掴む。
	 */
	private Map<String, Cookie> browserCookies(MvcResult result) {
		Map<String, Cookie> cookies = new LinkedHashMap<>();
		for (Cookie cookie : result.getResponse().getCookies()) {
			if (cookie.getMaxAge() == 0) {
				cookies.remove(cookie.getName());
			}
			else {
				cookies.put(cookie.getName(), cookie);
			}
		}
		return cookies;
	}

	private MvcResult performLogin(Cookie csrfToken) throws Exception {
		return this.mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(LOGIN_BODY)
				.cookie(csrfToken)
				.header("X-XSRF-TOKEN", csrfToken.getValue()))
			.andExpect(status().isOk())
			.andReturn();
	}

}
