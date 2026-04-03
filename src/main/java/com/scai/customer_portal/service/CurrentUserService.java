package com.scai.customer_portal.service;

import com.scai.customer_portal.domain.AppUser;
import com.scai.customer_portal.repository.AppUserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CurrentUserService {

	private final AppUserRepository appUserRepository;

	public CurrentUserService(AppUserRepository appUserRepository) {
		this.appUserRepository = appUserRepository;
	}

	public UUID currentUserId() {
		var auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof JwtAuthenticationToken jwtAuth) {
			Jwt jwt = jwtAuth.getToken();
			return UUID.fromString(jwt.getSubject());
		}
		throw new IllegalStateException("Not authenticated with JWT");
	}

	@Transactional(readOnly = true)
	public AppUser requireCurrentUser() {
		return appUserRepository.findById(currentUserId())
				.orElseThrow(() -> new IllegalStateException("User not found"));
	}
}
