package com.example.spacereserve.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * Spring Security の入口。フィルタチェーンとパスワードエンコーダを組み立てる。
 *
 * 認証はセッション（Cookie）方式で、クライアントは同一オリジンで配信する React。 CSRF は有効。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository,
			CsrfTokenRepository csrfTokenRepository) throws Exception {
		http.authorizeHttpRequests(auth -> auth.requestMatchers("/", "/index.html", "/favicon.ico", "/assets/**")
			.permitAll()
			.requestMatchers("/actuator/health", "/actuator/health/**")
			.permitAll()
			.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/users")
			.permitAll()
			.requestMatchers("/api/admin/**")
			.hasRole("ADMIN")
			.anyRequest()
			.authenticated())
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

		return http.build();
	}

	/**
	 * ログインに formLogin を使わないため、`SecurityContext` の保存は Controller の責務になる（authentication.md
	 * 4 節）。 Spring Security は既定ではこれを Bean として公開せずフィルタチェーンの内側に隠すので、Controller から注入できるよう
	 * 既定と同じ構成のものを明示的に立てる。
	 */
	@Bean
	SecurityContextRepository securityContextRepository() {
		return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(),
				new HttpSessionSecurityContextRepository());
	}

	/**
	 * `spa()` が内部で生成するものと同一構成。Bean にするのは、ログイン成功時に CSRF トークンを 更新する
	 * `CsrfAuthenticationStrategy`（LoginController）へ、フィルタチェーンが読むのと
	 * 同じ実体を渡す必要があるため。別実体だと更新したトークンが照合側に反映されない。
	 */
	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		return CookieCsrfTokenRepository.withHttpOnlyFalse();
	}

	/**
	 * BCryptPasswordEncoder を直接返さない。DelegatingPasswordEncoder はハッシュに {@code {bcrypt}}
	 * を前置して保存するため、後から Argon2 へ移る際に既存ハッシュと 同一カラムで共存させられる。直接指定すると移行時に全員へ再設定を強いることになる。
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder,
			AuthenticationEventPublisher eventPublisher) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		// 有効性チェックをパスワード照合の後ろへ移す（authentication.md 4 節）。
		// 既定の順序のままだと、パスワードが違っても「無効です」と返りアドレスの存在が漏れる。
		provider.setPreAuthenticationChecks(userDetails -> {
		});
		provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
		ProviderManager manager = new ProviderManager(provider);
		manager.setAuthenticationEventPublisher(eventPublisher);
		return manager;
	}

}
