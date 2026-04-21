package com.scai.customer_portal.api.dto;

import java.util.List;

/**
 * Result of {@code POST /api/issues/import-jira-bulk}: JQL search then per-key full import.
 */
public record BulkJiraImportResponse(
		int totalKeysFromJira,
		int importedOrUpdated,
		int failed,
		List<String> failureSamples
) {
}
