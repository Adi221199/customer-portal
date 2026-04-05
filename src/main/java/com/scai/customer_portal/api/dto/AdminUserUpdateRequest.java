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
 * <p>Pods: {@code podNames} takes precedence over {@code podIds} when non-null.
 * {@code null} = leave pods unchanged; empty list = clear all; non-empty = replace set.
 */
public record AdminUserUpdateRequest(
		@NotEmpty Set<PortalRole> roles,
		UUID organizationId,
		@Size(max = 200) String organizationName,
		List<UUID> podIds,
		@Valid
		List<@NotBlank @Size(max = 200) String> podNames,
		Boolean enabled
) {
}
