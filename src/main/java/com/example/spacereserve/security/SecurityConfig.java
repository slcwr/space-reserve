package com.example.spacereserve.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Spring Security の入口。フィルタチェーンとパスワードエンコーダを組み立てる。
 *
 * 認証はセッション（Cookie）方式で、クライアントは同一オリジンで配信する React。 CSRF は有効。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository)
			throws Exception {
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

			.csrf(CsrfConfigurer::spa)
			.formLogin(FormLoginConfigurer::disable)
			.httpBasic(HttpBasicConfigurer::disable)
			.requestCache(RequestCacheConfigurer::disable)
			.logout(logout -> logout.logoutUrl("/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			// ログインは Controller が自分で saveContext を呼ぶ。フィルタチェーンが読む先と
			// Controller が書く先を同じインスタンスに揃えるため、既定と同じ構成の Bean を明示的に渡す。
			.securityContext(context -> context.securityContextRepository(securityContextRepository))

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
	 * BCryptPasswordEncoder を直接返さない。DelegatingPasswordEncoder はハッシュに {@code {bcrypt}}
	 * を前置して保存するため、後から Argon2 へ移る際に既存ハッシュと 同一カラムで共存させられる。直接指定すると移行時に全員へ再設定を強いることになる。
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}
