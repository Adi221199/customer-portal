package com.scai.customer_portal.api.dto;

import com.scai.customer_portal.domain.IssueOrigin;
import com.scai.customer_portal.domain.IssueStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record IssueResponse(
		UUID id,
		String jiraIssueKey,
		String jiraIssueId,
		/** Shown when {@link #jiraIssueKey} is null (portal-raised). */
		String portalReference,
		IssueOrigin issueOrigin,
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
		List<IssueAttachmentInfo> attachments,
		UUID organizationId,
		String organizationName,
		UUID assigneeId,
		String assigneeEmail,
		String assigneeDisplayName,
		UUID portalReporterId,
		String portalReporterEmail,
		String jiraReporterEmail,
		String jiraReporterDisplayName,
		UUID importedById,
		String importedByEmail,
		Instant lastSyncedAt
) {
}
