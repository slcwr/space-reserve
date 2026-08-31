package com.example.spacereserve.common.security;

import com.example.spacereserve.common.domain.User;
import com.example.spacereserve.common.repository.UserMapper;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

	private final UserMapper userMapper;

	AppUserDetailsService(UserMapper userMapper) {
		this.userMapper = userMapper;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		User user = this.userMapper.findByEmail(username)
			.orElseThrow(() -> new UsernameNotFoundException("user not found"));
		return new AppUserDetails(user.getId(), user.getEmail(), user.getPasswordHash(), user.getDisplayName(),
				user.isEnabled());
	}

}
