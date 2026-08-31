package com.example.spacereserve.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * user / admin の両アプリが共有する認証部品。
 *
 * ここには「誰であるか」を決める仕組みだけを置き、「どの URL に誰を通すか」は置かない。 後者はアプリごとに違うので、各モジュールの SecurityFilterChain
 * が持つ （UserSecurityConfig / AdminSecurityConfig）。
 *
 * SecurityFilterChain をここに置かないのは、common が web アプリであることを前提にしないため。 EnableWebSecurity
 * も同じ理由でアプリ側に付ける。
 */
@Configuration
public class CommonSecurityConfig {

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
	 *
	 * 両アプリで同じものを使うこと。片方だけ変えると、一方で作ったハッシュが他方で照合できなくなる。
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
