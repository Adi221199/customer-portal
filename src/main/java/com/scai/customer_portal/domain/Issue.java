package com.scai.customer_portal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "issues", indexes = {
		@Index(name = "idx_issues_organization_id", columnList = "organization_id"),
		@Index(name = "idx_issues_issue_date", columnList = "issue_date"),
		@Index(name = "idx_issues_environment", columnList = "environment"),
		@Index(name = "idx_issues_module", columnList = "module"),
		@Index(name = "idx_issues_category", columnList = "category"),
		@Index(name = "idx_issues_severity", columnList = "severity"),
		@Index(name = "idx_issues_assignee_id", columnList = "assignee_id"),
		@Index(name = "idx_issues_jira_issue_key", columnList = "jira_issue_key")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Issue {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "jira_issue_key", unique = true)
	private String jiraIssueKey;

	@Column(name = "jira_issue_id")
	private String jiraIssueId;

	@Column(nullable = false)
	private String title;

	@Column(columnDefinition = "text")
	private String description;

	@Column(name = "issue_date")
	private LocalDate issueDate;

	@Column(name = "closing_date")
	private LocalDate closingDate;

	private String module;
	private String environment;
	private String category;

	/** 1 = high, 2 = moderate, 3 = low */
	private Integer severity;

	@Column(name = "rca_description", columnDefinition = "text")
	private String rcaDescription;

	@Column(name = "jira_status")
	private String jiraStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	@Builder.Default
	private IssueStatus portalStatus = IssueStatus.OPEN;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assignee_id")
	private AppUser assignee;

	/** Portal user whose email matches Jira reporter (ticket creator in Jira). Used for SC_AGENT visibility. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "portal_reporter_id")
	private AppUser portalReporter;

	@Column(name = "jira_reporter_email")
	private String jiraReporterEmail;

	@Column(name = "jira_reporter_display_name")
	private String jiraReporterDisplayName;

	@Column(name = "jira_reporter_account_id")
	private String jiraReporterAccountId;

	@Column(name = "jira_assignee_account_id")
	private String jiraAssigneeAccountId;

	@Column(name = "jira_assignee_email")
	private String jiraAssigneeEmail;

	@Column(name = "jira_assignee_display_name")
	private String jiraAssigneeDisplayName;

	/** User who ran import in the portal (audit). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by_id", nullable = false)
	private AppUser createdBy;

	@Column(name = "last_synced_at")
	private Instant lastSyncedAt;

	@Column(name = "jira_snapshot", columnDefinition = "text")
	private String jiraSnapshotJson;
}
