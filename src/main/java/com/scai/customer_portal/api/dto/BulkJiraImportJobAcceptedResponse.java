package com.scai.customer_portal.api.dto;

import java.util.UUID;

/** Immediate response from {@code POST /api/issues/import-jira-bulk} when the import runs in the background. */
public record BulkJiraImportJobAcceptedResponse(UUID jobId, String status) {
}
