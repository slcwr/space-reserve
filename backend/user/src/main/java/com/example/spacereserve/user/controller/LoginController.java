package com.example.spacereserve.user.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.bind.annotation.*;
import com.example.spacereserve.user.dto.request.LoginRequest;
import com.example.spacereserve.user.dto.response.UserResponse;
import com.example.spacereserve.common.security.AppUserDetails;
import com.example.spacereserve.common.service.LoginAttemptService;

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

	private final LoginAttemptService loginAttemptService;

	private LoginController(AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository,
			LoginAttemptService loginAttemptService) {
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.loginAttemptService = loginAttemptService;
		// 認証成功時に振り直すのはセッション ID だけでは足りない。CSRF トークンを据え置くと、
		// 攻撃者が事前に固定したトークンがログイン後もそのまま通る。formLogin を使う場合に
		// Spring Security が既定で組む2本と同じ構成を、ここで自前で作る。
		this.sessionAuthenticationStrategy = new CompositeSessionAuthenticationStrategy(
				List.of(new ChangeSessionIdAuthenticationStrategy(), csrfAuthenticationStrategy(csrfTokenRepository)));
	}

	private static CsrfAuthenticationStrategy csrfAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
		CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
		requestHandler.setCsrfRequestAttributeName(null);
		CsrfAuthenticationStrategy strategy = new CsrfAuthenticationStrategy(csrfTokenRepository);
		strategy.setRequestHandler(requestHandler);
		return strategy;
	}

	@PostMapping("/login")
	public UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		// 送信元 IP はここで取り出し、Service へは String で渡す。Service に HttpServletRequest を
		// 持ち込むと HTTP から独立させるという層の約束が崩れる（service/package-info.java）。
		String clientIp = httpRequest.getRemoteAddr();
		this.loginAttemptService.verifyNotBlocked(request.email(), clientIp);
		Authentication auth = authenticate(request, clientIp);
		this.sessionAuthenticationStrategy.onAuthentication(auth, httpRequest, httpResponse);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(auth);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, httpRequest, httpResponse);
		return UserResponse.from((AppUserDetails) auth.getPrincipal());
	}

	/**
	 * 認証し、その結果で試行回数を更新する。
	 *
	 * `InternalAuthenticationServiceException`（DB 断など）は数えない。資格情報の誤りではないため、
	 * これを数えると障害中のリトライで正規の利用者が締め出される。
	 */
	private Authentication authenticate(LoginRequest request, String clientIp) {
		Authentication auth;
		try {
			auth = this.authenticationManager
				.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
		}
		catch (AuthenticationException ex) {
			if (!(ex instanceof InternalAuthenticationServiceException)) {
				this.loginAttemptService.recordFailure(request.email(), clientIp);
			}
			throw ex;
		}
		this.loginAttemptService.reset(request.email(), clientIp);
		return auth;
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
