package com.scai.customer_portal.api.dto;

import com.scai.customer_portal.domain.PortalRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Organization: set via {@code organizationId} (wins if both set) or {@code organizationName} (trimmed, case-insensitive).
 * Omit both fields to leave organization unchanged. Blank {@code organizationName} clears organization.
 *
 * <p>{@code moduleNames}: Jira project / module codes (e.g. EDM, DPAI) for {@code SC_LEAD} scope.
 * {@code null} = leave unchanged; empty list = clear all; non-empty = replace set.
 */
public record AdminUserUpdateRequest(
		@NotEmpty Set<PortalRole> roles,
		UUID organizationId,
		@Size(max = 200) String organizationName,
		@Valid
		List<@NotBlank @Size(max = 200) String> moduleNames,
		Boolean enabled
) {
}
