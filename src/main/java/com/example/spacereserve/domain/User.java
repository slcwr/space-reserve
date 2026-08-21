package com.example.spacereserve.domain;

import java.time.LocalDateTime;

public class User {

	private Long id;

	private String email;

	private String passwordHash;

	private String displayName;

	private Role role;

	private boolean enabled;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	private User() {
	}

	public static User signUp(String email, String passwordHash, String displayName) {
		User user = new User();
		user.email = email;
		user.passwordHash = passwordHash;
		user.displayName = displayName;
		user.role = Role.USER;
		user.enabled = true;
		user.createdAt = LocalDateTime.now();
		user.updatedAt = user.createdAt;
		return user;
	}

	public Long getId() {
		return this.id;
	}

	public String getEmail() {
		return this.email;
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public Role getRole() {
		return this.role;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public LocalDateTime getCreatedAt() {
		return this.createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return this.updatedAt;
	}

	public boolean isAdmin() {
		return this.role == Role.ADMIN;
	}

	public void changePassword(String newPasswordHash) {
		this.passwordHash = newPasswordHash;
		this.updatedAt = LocalDateTime.now();
	}

	public void disable() {
		this.enabled = false;
		this.updatedAt = LocalDateTime.now();
	}

	public void enable() {
		this.enabled = true;
		this.updatedAt = LocalDateTime.now();
	}

	@Override
	public String toString() {
		return "User[id=" + this.id + ", email=" + this.email + ", role=" + this.role + ", enabled=" + this.enabled
				+ "]";
	}

}
