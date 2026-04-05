package com.scai.customer_portal.service;

import com.scai.customer_portal.api.dto.AuthResponse;
import com.scai.customer_portal.api.dto.LoginRequest;
import com.scai.customer_portal.api.dto.RegisterRequest;
import com.scai.customer_portal.api.dto.UserResponse;
import com.scai.customer_portal.domain.AppUser;
import com.scai.customer_portal.domain.Organization;
import com.scai.customer_portal.domain.PortalRole;
import com.scai.customer_portal.config.JwtProperties;
import com.scai.customer_portal.repository.AppUserRepository;
import com.scai.customer_portal.repository.OrganizationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class AuthService {

	private final AppUserRepository appUserRepository;
	private final OrganizationRepository organizationRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenService jwtTokenService;
	private final JwtProperties jwtProperties;
	private final UserResponseMapper userResponseMapper;

	public AuthService(
			AppUserRepository appUserRepository,
			OrganizationRepository organizationRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenService jwtTokenService,
			JwtProperties jwtProperties,
			UserResponseMapper userResponseMapper) {
		this.appUserRepository = appUserRepository;
		this.organizationRepository = organizationRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenService = jwtTokenService;
		this.jwtProperties = jwtProperties;
		this.userResponseMapper = userResponseMapper;
	}

	@Transactional
	public UserResponse register(RegisterRequest request) {
		if (appUserRepository.existsByEmailIgnoreCase(request.email())) {
			throw new IllegalArgumentException("Email already registered");
		}
		Organization org = resolveOrganizationForRegistration(request.organizationName());
		AppUser user = AppUser.builder()
				.email(request.email().trim().toLowerCase())
				.passwordHash(passwordEncoder.encode(request.password()))
				.displayName(request.displayName().trim())
				.enabled(true)
				.organization(org)
				.roles(Set.of(PortalRole.CUSTOMER_USER))
				.build();
		user = appUserRepository.save(user);
		return userResponseMapper.toResponse(user);
	}

	private Organization resolveOrganizationForRegistration(String organizationName) {
		if (organizationName == null || organizationName.isBlank()) {
			return null;
		}
		String name = organizationName.trim();
		return organizationRepository.findByNameIgnoreCase(name)
				.orElseGet(() -> organizationRepository.save(Organization.builder().name(name).build()));
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		AppUser user = appUserRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase())
				.orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
		if (!user.isEnabled()) {
			throw new IllegalArgumentException("Account disabled");
		}
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new IllegalArgumentException("Invalid email or password");
		}
		String token = jwtTokenService.createAccessToken(user);
		return AuthResponse.of(token, jwtProperties.expirationSeconds());
	}
}
