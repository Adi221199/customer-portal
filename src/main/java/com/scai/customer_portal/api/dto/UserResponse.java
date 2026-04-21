package com.scai.customer_portal.api.dto;

import com.scai.customer_portal.domain.PortalRole;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
		UUID id,
		String email,
		String displayName,
		boolean enabled,
		UUID organizationId,
		String organizationName,
		List<String> assignedModules,
		Set<PortalRole> roles
) {
}
