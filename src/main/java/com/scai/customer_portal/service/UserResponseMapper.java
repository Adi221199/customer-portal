package com.scai.customer_portal.service;

import com.scai.customer_portal.api.dto.UserResponse;
import com.scai.customer_portal.domain.AppUser;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class UserResponseMapper {

	public UserResponse toResponse(AppUser u) {
		List<String> modules = u.getAssignedModules() == null ? List.of()
				: u.getAssignedModules().stream()
				.filter(s -> s != null && !s.isBlank())
				.map(String::trim)
				.distinct()
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
		return new UserResponse(
				u.getId(),
				u.getEmail(),
				u.getDisplayName(),
				u.isEnabled(),
				u.getOrganization() != null ? u.getOrganization().getId() : null,
				u.getOrganization() != null ? u.getOrganization().getName() : null,
				modules,
				u.getRoles());
	}
}
