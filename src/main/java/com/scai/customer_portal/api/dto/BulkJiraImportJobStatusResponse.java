package com.scai.customer_portal.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Poll {@code GET /api/issues/import-jira-bulk/jobs/{jobId}} while the background job runs. */
public record BulkJiraImportJobStatusResponse(
		UUID jobId,
		String status,
		int totalKeysFromJira,
		int importedOrUpdated,
		int failed,
		/** {@code importedOrUpdated + failed} — progress while {@code status} is RUNNING. */
		int processed,
		List<String> failureSamples,
		String fatalError,
		Instant createdAt,
		Instant startedAt,
		Instant completedAt
) {
}
