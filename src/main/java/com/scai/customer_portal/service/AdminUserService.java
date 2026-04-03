package com.scai.customer_portal.service;

import com.scai.customer_portal.api.dto.AdminUserUpdateRequest;
import com.scai.customer_portal.api.dto.UserResponse;
import com.scai.customer_portal.domain.AppUser;
import com.scai.customer_portal.repository.AppUserRepository;
import com.scai.customer_portal.repository.OrganizationRepository;
import com.scai.customer_portal.repository.PodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class AdminUserService {

	private final AppUserRepository appUserRepository;
	private final OrganizationRepository organizationRepository;
	private final PodRepository podRepository;
	private final UserResponseMapper userResponseMapper;

	public AdminUserService(
			AppUserRepository appUserRepository,
			OrganizationRepository organizationRepository,
			PodRepository podRepository,
			UserResponseMapper userResponseMapper) {
		this.appUserRepository = appUserRepository;
		this.organizationRepository = organizationRepository;
		this.podRepository = podRepository;
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
		if (request.organizationId() != null) {
			user.setOrganization(organizationRepository.findById(request.organizationId())
					.orElseThrow(() -> new IllegalArgumentException("Organization not found")));
		}
		else {
			user.setOrganization(null);
		}
		if (request.podId() != null) {
			user.setPod(podRepository.findById(request.podId())
					.orElseThrow(() -> new IllegalArgumentException("Pod not found")));
		}
		else {
			user.setPod(null);
		}
		if (request.enabled() != null) {
			user.setEnabled(request.enabled());
		}
		return userResponseMapper.toResponse(appUserRepository.save(user));
	}
}
