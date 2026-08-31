package com.example.spacereserve.common.domain;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class User {

	private Long id;

	private String email;

	private String passwordHash;

	private String displayName;

	private boolean enabled;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

}
