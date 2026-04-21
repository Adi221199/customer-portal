package com.scai.customer_portal.api.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Optional filters for {@code POST /api/issues/import-jira-bulk}. All null/empty = default import (Customer and
 * Environment non-empty, optional {@code jira.bulk-import-extra-jql}).
 */
public record BulkJiraImportRequest(
		/** Exact Jira Customer labels (OR within this list). */
		List<@Size(max = 256) String> customerNames,
		/** Exact Jira Environment / Env values (OR within this list). */
		List<@Size(max = 256) String> environments,
		/** Jira project keys (e.g. EDM, DPAI) — {@code project in (...)}. */
		List<@Size(max = 32) String> projectKeys,
		/**
		 * When {@code true} (default): require Customer and Environment populated in Jira.
		 * When {@code false}: that pair of constraints is skipped — narrow with {@code projectKeys},
		 * {@code customerNames}, {@code environments}, and/or {@code jira.bulk-import-extra-jql}.
		 */
		Boolean requireCustomerAndEnvironmentNonEmpty
) {

	public static BulkJiraImportRequest defaults() {
		return new BulkJiraImportRequest(null, null, null, null);
	}

	/** Default {@code true} when the Boolean component is null. */
	public boolean strictCustomerAndEnv() {
		return requireCustomerAndEnvironmentNonEmpty == null || Boolean.TRUE.equals(requireCustomerAndEnvironmentNonEmpty);
	}
}
