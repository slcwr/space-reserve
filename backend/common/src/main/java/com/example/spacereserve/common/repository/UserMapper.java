package com.example.spacereserve.common.repository;

import java.util.Optional;

import com.example.spacereserve.common.domain.User;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

	@Select("""
			SELECT id, email, password_hash, display_name, role, enabled, created_at, updated_at
			  FROM users
			 WHERE email = #{email}
			""")
	Optional<User> findByEmail(String email);

}
