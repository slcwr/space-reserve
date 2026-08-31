package com.example.spacereserve.user.dto.response;

import com.example.spacereserve.common.security.AppUserDetails;

public record UserResponse(Long id, String email, String displayName) {

	public static UserResponse from(AppUserDetails principal) {
		return new UserResponse(principal.getUserId(), principal.getUsername(), principal.getDisplayName());
	}

}
