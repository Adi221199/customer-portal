package com.scai.customer_portal.service;

import com.scai.customer_portal.api.dto.AssignedPodRef;
import com.scai.customer_portal.api.dto.UserResponse;
import com.scai.customer_portal.domain.AppUser;
import com.scai.customer_portal.domain.Pod;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class UserResponseMapper {

	public UserResponse toResponse(AppUser u) {
		List<AssignedPodRef> pods = u.getPods() == null ? List.of()
				: u.getPods().stream()
				.sorted(Comparator.comparing(Pod::getName, String.CASE_INSENSITIVE_ORDER))
				.map(p -> new AssignedPodRef(p.getId(), p.getName()))
				.toList();
		return new UserResponse(
				u.getId(),
				u.getEmail(),
				u.getDisplayName(),
				u.isEnabled(),
				u.getOrganization() != null ? u.getOrganization().getId() : null,
				u.getOrganization() != null ? u.getOrganization().getName() : null,
				pods,
				u.getRoles());
	}
}
