package com.scai.customer_portal.service;

import com.scai.customer_portal.api.dto.AdminUserUpdateRequest;
import com.scai.customer_portal.api.dto.UserResponse;
import com.scai.customer_portal.domain.AppUser;
import com.scai.customer_portal.repository.AppUserRepository;
import com.scai.customer_portal.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class AdminUserService {

	private final AppUserRepository appUserRepository;
	private final OrganizationRepository organizationRepository;
	private final UserResponseMapper userResponseMapper;

	public AdminUserService(
			AppUserRepository appUserRepository,
			OrganizationRepository organizationRepository,
			UserResponseMapper userResponseMapper) {
		this.appUserRepository = appUserRepository;
		this.organizationRepository = organizationRepository;
		this.userResponseMapper = userResponseMapper;
	}

	@Transactional(readOnly = true)
	public List<UserResponse> listUsers() {
		return appUserRepository.findAll().stream().map(userResponseMapper::toResponse).toList();
	}

	@Transactional
	public UserResponse updateUser(UUID userId, AdminUserUpdateRequest request) {
		AppUser user = appUserRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("User not found"));
		user.setRoles(new HashSet<>(request.roles()));
		applyOrganization(user, request);
		applyModules(user, request);
		if (request.enabled() != null) {
			user.setEnabled(request.enabled());
		}
		return userResponseMapper.toResponse(appUserRepository.save(user));
	}

	private void applyOrganization(AppUser user, AdminUserUpdateRequest request) {
		if (request.organizationId() != null) {
			user.setOrganization(organizationRepository.findById(request.organizationId())
					.orElseThrow(() -> new IllegalArgumentException("Organization not found")));
			return;
		}
		if (request.organizationName() != null) {
			String name = request.organizationName().trim();
			if (name.isEmpty()) {
				user.setOrganization(null);
				return;
			}
			user.setOrganization(organizationRepository.findByNameIgnoreCase(name)
					.orElseThrow(() -> new IllegalArgumentException("Organization not found: " + name)));
		}
	}

	private void applyModules(AppUser user, AdminUserUpdateRequest request) {
		if (request.moduleNames() == null) {
			return;
		}
		user.getAssignedModules().clear();
		for (String raw : request.moduleNames()) {
			if (raw == null || raw.isBlank()) {
				continue;
			}
			user.getAssignedModules().add(raw.trim());
		}
	}
}
