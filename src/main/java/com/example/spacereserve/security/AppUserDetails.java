package com.example.spacereserve.security;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.example.spacereserve.domain.Role;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 認証済みユーザー
 */
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

	private final Role role;

	private final boolean enabled;

	public AppUserDetails(Long userId, String email, String passwordHash, String displayName, Role role,
			boolean enabled) {
		this.userId = Objects.requireNonNull(userId, "userId");
		this.email = Objects.requireNonNull(email, "email");
		this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
		this.displayName = Objects.requireNonNull(displayName, "displayName");
		this.role = Objects.requireNonNull(role, "role");
		this.enabled = enabled;
	}

	/**
	 * 業務側でユーザーを識別する ID。Controller から Service へ渡すのはこの値だけにする。
	 */
	public Long getUserId() {
		return this.userId;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public Role getRole() {
		return this.role;
	}

	/**
	 * `ROLE_` を前置する。SecurityConfig の hasRole("ADMIN") は権限名 "ROLE_ADMIN" を探すため、 これを落とすと
	 * /api/admin/** が誰も通らなくなる。
	 */
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
	}

	/**
	 * 照合に使うのは保存済みハッシュ。DaoAuthenticationProvider が PasswordEncoder 越しに 突き合わせる。
	 */
	@Override
	public String getPassword() {
		return this.passwordHash;
	}

	/**
	 * ログイン ID はメールアドレス。UserDetailsService の引数もこの値になる。
	 */
	@Override
	public String getUsername() {
		return this.email;
	}

	/**
	 * 既定実装が true を返すため、退職者・停止アカウントを弾くにはこの override が必須。 チェックがパスワード照合の後ろで走るよう、
	 * AppUserDetailsService 側で postAuthenticationChecks に
	 * 移すこと。既定の順序のままだと、パスワードが違っても「無効です」と返り アドレスの存在が漏れる（authentication.md 4 節）。
	 */
	@Override
	public boolean isEnabled() {
		return this.enabled;
	}

	/**
	 * 同一ユーザーの判定は ID で行う。セッションを Redis から復元すると別インスタンスになるため、 同時ログイン制御（SessionRegistry）を入れる際に
	 * 参照比較では成立しない。
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
		return "AppUserDetails[userId=" + this.userId + ", email=" + this.email + ", role=" + this.role + ", enabled="
				+ this.enabled + "]";
	}

}
