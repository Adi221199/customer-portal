package com.scai.customer_portal.api.dto;

import com.scai.customer_portal.domain.PortalRole;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;
import java.util.UUID;

public record AdminUserUpdateRequest(
		@NotEmpty Set<PortalRole> roles,
		UUID organizationId,
		UUID podId,
		Boolean enabled
) {
}
