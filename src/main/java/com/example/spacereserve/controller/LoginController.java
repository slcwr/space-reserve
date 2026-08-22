package com.example.spacereserve.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import com.example.spacereserve.dto.request.LoginRequest;
import com.example.spacereserve.dto.response.UserResponse;
import com.example.spacereserve.security.AppUserDetails;

/**
 * ログイン認証を担当するコントローラ。 構成: セッション方式(サーバー側でログイン状態を保持) + REST(JSONを返す)。 ログアウトは Spring Security
 * の /logout に任せるため、ここには書かない。
 */
@RestController
@RequestMapping("/api/auth")
public class LoginController {

	private final AuthenticationManager authenticationManager;

	private final SecurityContextRepository securityContextRepository;

	private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

	private LoginController(AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository) {
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		// 認証成功時に振り直すのはセッション ID だけでは足りない。CSRF トークンを据え置くと、
		// 攻撃者が事前に固定したトークンがログイン後もそのまま通る。formLogin を使う場合に
		// Spring Security が既定で組む2本と同じ構成を、ここで自前で作る。
		this.sessionAuthenticationStrategy = new CompositeSessionAuthenticationStrategy(List
			.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokenRepository)));
	}

	@PostMapping("/login")
	public UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		Authentication auth = authenticationManager
			.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
		this.sessionAuthenticationStrategy.onAuthentication(auth, httpRequest, httpResponse);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(auth);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, httpRequest, httpResponse);
		return UserResponse.from((AppUserDetails) auth.getPrincipal());
	}

	/**
	 * ログイン中のユーザーを返す。フロントがリロード後にログイン状態を復元するための口。 セッションの中身はサーバ側にしか無く、Cookie
	 * からは何も読めないため、これが無いとフロントは 保護 API を叩いて 401 を見るまで自分の状態を判断できない。
	 *
	 * 副次的に CSRF トークンの取得口も兼ねる。`CsrfFilter` は `AuthorizationFilter` より前段にあるので、 未ログインで 401
	 * になる場合でも `Set-Cookie: XSRF-TOKEN` は返る。開発時は `GET /` を Vite が返して Spring
	 * を通らないため、この口を1回叩かないと最初のログイン POST が 403 になる （authentication.md 12 節）。
	 */
	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal AppUserDetails user) {
		return UserResponse.from(user);
	}

}
