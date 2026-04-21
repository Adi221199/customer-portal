package com.scai.customer_portal.api.dto;

import java.util.List;
import java.util.UUID;

/** Options for building bulk-import filters in the UI ({@code SC_ADMIN} only). {@code modules} = Jira project keys from existing issues (e.g. EDM, DPAI). */
public record BulkImportOptionsResponse(
		List<NameLabelOption> organizations,
		List<NameLabelOption> modules,
		List<String> environments,
		/** Every portal organization (id + name) for admin pickers; {@link #organizations()} stays name-based for Jira bulk filters. */
		List<PortalOrganizationOption> portalOrganizations
) {
	public record NameLabelOption(String value, String label) {
	}

	public record PortalOrganizationOption(UUID id, String name) {
	}
}
