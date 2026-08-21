package com.example.spacereserve.dto.response;

import com.example.spacereserve.domain.Role;
import com.example.spacereserve.security.AppUserDetails;

public record UserResponse(Long id, String email, String displayName, Role role) {

	public static UserResponse from(AppUserDetails principal) {
		return new UserResponse(principal.getUserId(), principal.getUsername(), principal.getDisplayName(),
				principal.getRole());
	}

}
