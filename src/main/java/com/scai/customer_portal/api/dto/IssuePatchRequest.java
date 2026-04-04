package com.scai.customer_portal.api.dto;

import com.scai.customer_portal.domain.IssueStatus;

import java.util.UUID;

/**
 * Partial update. Use {@code unassignAssignee} / {@code clearPod} / {@code clearRcaDescription} to clear.
 * {@code module}, {@code environment}, {@code category}: non-null value applies (blank string clears the field).
 */
public record IssuePatchRequest(
		UUID organizationId,
		UUID assigneeId,
		Boolean unassignAssignee,
		UUID podId,
		Boolean clearPod,
		UUID portalReporterUserId,
		Boolean clearPortalReporter,
		String rcaDescription,
		Boolean clearRcaDescription,
		IssueStatus portalStatus,
		String module,
		String environment,
		String category
) {
}
