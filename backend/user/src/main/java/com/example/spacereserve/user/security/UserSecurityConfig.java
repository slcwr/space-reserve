package com.example.spacereserve.user.security;

import com.example.spacereserve.common.security.ApiSecurityDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * 利用者向けアプリの認可ルール。
 *
 * 認証の部品（PasswordEncoder、AuthenticationManager など）は common の CommonSecurityConfig が
 * 持ち、ここは「どの URL に誰を通すか」だけを決める。
 *
 * `/api/admin/**` のルールはここには無い。管理 API は admin アプリ（別プロセス）が受け持つため、
 * このアプリのフィルタチェーンに書いても到達しない経路の記述になる。
 */
@Configuration
@EnableWebSecurity
public class UserSecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository,
			CsrfTokenRepository csrfTokenRepository) throws Exception {
		http.authorizeHttpRequests(auth -> auth.requestMatchers("/", "/index.html", "/favicon.ico", "/assets/**")
			.permitAll()
			.requestMatchers("/actuator/health", "/actuator/health/**")
			.permitAll()
			.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/users")
			.permitAll()
			.anyRequest()
			.authenticated());

		ApiSecurityDefaults.apply(http, securityContextRepository, csrfTokenRepository);

		return http.build();
	}

}
