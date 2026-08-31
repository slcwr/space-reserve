package com.example.spacereserve.admin.security;

import com.example.spacereserve.common.security.ApiSecurityDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * 管理向けアプリの認可ルール。ヘルスチェック以外はすべて ROLE_ADMIN を要求する。
 *
 * アプリ自体を管理者専用にしているので、Controller 側で個別に権限を書く必要はない。 これが「別プロセスに分ける」ことの実利で、URL
 * の書き漏らしが管理機能の露出に直結しない。
 *
 * <p>
 * <b>未了: このアプリにはログイン口が無い。</b> セッションは user アプリと同じ Redis 名前空間
 * （`space-reserve:session`）を共有するため、同一ホストの別ポートで動かす開発環境では user アプリで取った Cookie
 * がそのまま通る。別ホストで運用する場合はこの前提が崩れるので、 admin 側にもログイン口を置くか、リバースプロキシで同一オリジンに揃えるかを決める必要がある。
 *
 * <p>
 * <b>未了: 現状この条件を満たせる利用者はいない。</b> AppUserDetails は権限を "ROLE_USER" で固定しており、Role 列挙が削除されたため
 * ADMIN を表す手段が無い。 admin アプリを実際に動かすには、ロールの持ち方を決め直すところから必要になる。
 */
@Configuration
@EnableWebSecurity
public class AdminSecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository,
			CsrfTokenRepository csrfTokenRepository) throws Exception {
		http.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/actuator/health/**")
			.permitAll()
			.anyRequest()
			.hasRole("ADMIN"));

		ApiSecurityDefaults.apply(http, securityContextRepository, csrfTokenRepository);

		return http.build();
	}

}
