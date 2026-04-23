package com.scai.customer_portal.domain;

/**
 * Distinguishes Jira-sourced issues from those raised in the customer portal. Same table as imported issues.
 */
public enum IssueOrigin {
	/** Migrated or imported from Jira */
	JIRA,
	/** Created in the portal (no Jira key yet) */
	PORTAL
}
