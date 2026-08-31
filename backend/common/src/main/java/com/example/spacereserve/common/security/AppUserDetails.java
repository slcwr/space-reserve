package com.example.spacereserve.common.security;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import lombok.Data;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 認証済みユーザー
 */
@Data
public final class AppUserDetails implements UserDetails {

	/**
	 * 既定の JDK シリアライズでは、フィールドを1本足しただけで Redis 上の既存セッションが 復元できなくなる。開発中にこの形が変わったら `redis-cli
	 * FLUSHDB` で捨てること。
	 */
	private static final long serialVersionUID = 1L;

	private final Long userId;

	private final String email;

	private final String passwordHash;

	private final String displayName;

	private final boolean enabled;

	public AppUserDetails(Long userId, String email, String passwordHash, String displayName, boolean enabled) {
		this.userId = Objects.requireNonNull(userId, "userId");
		this.email = Objects.requireNonNull(email, "email");
		this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
		this.displayName = Objects.requireNonNull(displayName, "displayName");
		this.enabled = enabled;
	}

	/**
	 * `ROLE_` を前置する。SecurityConfig の hasRole("ADMIN") は権限名 "ROLE_ADMIN" を探すため、 これを落とすと
	 * /api/admin/** が誰も通らなくなる。
	 */
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_USER"));
	}

	/**
	 * 同一ユーザーの判定は ID で行う。セッションを Redis から復元すると別インスタンスになるため、 同時ログイン制御（SessionRegistry）を入れる際に
	 * 参照比較では成立しない。
	 *
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof AppUserDetails other)) {
			return false;
		}
		return this.userId.equals(other.userId);
	}

	@Override
	public int hashCode() {
		return this.userId.hashCode();
	}

	/**
	 * passwordHash を含めないこと。例外時に principal ごとログへ出る経路がある （authentication.md 2 節）。
	 */
	@Override
	public String toString() {
		return "AppUserDetails[userId=" + this.userId + ", email=" + this.email + " enabled=" + this.enabled + "]";
	}

	@Override
	public String getUsername() {
		return this.email;
	}

	@Override
	public String getPassword() {
		return this.passwordHash;
	}

}
