package com.scai.customer_portal.api.dto;

import com.scai.customer_portal.domain.PortalRole;

import java.util.Set;
import java.util.UUID;

public record UserResponse(
		UUID id,
		String email,
		String displayName,
		boolean enabled,
		UUID organizationId,
		String organizationName,
		UUID podId,
		String podName,
		Set<PortalRole> roles
) {
}
