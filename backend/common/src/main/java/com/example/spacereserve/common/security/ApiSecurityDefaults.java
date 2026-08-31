package com.example.spacereserve.common.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * user / admin のフィルタチェーンで共通する組み立て。
 *
 * 認可ルール（どの URL に誰を通すか）はアプリごとに違うのでここには置かない。 呼び出し側が {@code authorizeHttpRequests}
 * を書いたうえで、この定型部分を被せる。
 *
 * Bean にせず static メソッドにしているのは、HttpSecurity がフィルタチェーンごとの ビルダーであって共有できないため。Bean
 * にすると片方のアプリの設定が他方に漏れる余地ができる。
 */
public final class ApiSecurityDefaults {

	private ApiSecurityDefaults() {
	}

	/**
	 * セッション + Cookie による REST API という前提の共通設定を適用する。
	 *
	 * どちらのアプリも SPA からの利用で、リダイレクトではなくステータスコードで応答する点が共通する。
	 */
	public static HttpSecurity apply(HttpSecurity http, SecurityContextRepository securityContextRepository,
			CsrfTokenRepository csrfTokenRepository) throws Exception {
		return http
			// spa() は CookieCsrfTokenRepository と SpaCsrfTokenRequestHandler を代入するだけなので、
			// 後から csrfTokenRepository() を繋ぐとリポジトリだけが差し替わり、BREACH 対策の
			// ハンドラは残る。Controller がログイン時のトークン更新に同じ実体を要るため Bean 経由にする。
			.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
			.formLogin(FormLoginConfigurer::disable)
			.httpBasic(HttpBasicConfigurer::disable)
			.requestCache(RequestCacheConfigurer::disable)
			.logout(logout -> logout.logoutUrl("/api/auth/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			// ログインは Controller が自分で saveContext を呼ぶ。フィルタチェーンが読む先と
			// Controller が書く先を同じインスタンスに揃えるため、既定と同じ構成の Bean を明示的に渡す。
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			// 未認証は 401 を返すだけにする。既定はログイン画面へリダイレクトするため SPA では使えない。
			// 403 は既定のハンドラ（/error 経由）に任せる。
			.exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
	}

}
