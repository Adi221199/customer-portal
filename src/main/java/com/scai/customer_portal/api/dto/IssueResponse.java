package com.scai.customer_portal.api.dto;

import com.scai.customer_portal.domain.IssueStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record IssueResponse(
		UUID id,
		String jiraIssueKey,
		String jiraIssueId,
		String title,
		String description,
		LocalDate issueDate,
		LocalDate closingDate,
		String module,
		String environment,
		String category,
		Integer severity,
		String rcaDescription,
		String jiraStatus,
		IssueStatus portalStatus,
		UUID organizationId,
		String organizationName,
		UUID podId,
		String podName,
		UUID assigneeId,
		String assigneeEmail,
		UUID portalReporterId,
		String portalReporterEmail,
		String jiraReporterEmail,
		String jiraReporterDisplayName,
		UUID importedById,
		String importedByEmail,
		Instant lastSyncedAt
) {
}
