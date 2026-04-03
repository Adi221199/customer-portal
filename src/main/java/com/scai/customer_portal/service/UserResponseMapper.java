package com.scai.customer_portal.service;

import com.scai.customer_portal.api.dto.UserResponse;
import com.scai.customer_portal.domain.AppUser;
import org.springframework.stereotype.Component;

@Component
public class UserResponseMapper {

	public UserResponse toResponse(AppUser u) {
		return new UserResponse(
				u.getId(),
				u.getEmail(),
				u.getDisplayName(),
				u.isEnabled(),
				u.getOrganization() != null ? u.getOrganization().getId() : null,
				u.getOrganization() != null ? u.getOrganization().getName() : null,
				u.getPod() != null ? u.getPod().getId() : null,
				u.getPod() != null ? u.getPod().getName() : null,
				u.getRoles());
	}
}
