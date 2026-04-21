package com.scai.customer_portal.dashboard;

/**
 * Slicer dimensions. When building distinct values for one facet, other facets' filters still apply,
 * but this facet's own filter is omitted (Power BI slicer behaviour).
 */
public enum DashboardFacet {
	ORGANIZATION,
	/** Jira reporter / portal reporter (SPOC), not assignee. */
	SPOC,
	SEVERITY,
	ENVIRONMENT,
	MONTH,
	RCA,
	CATEGORY,
	MODULE,
	JIRA_KEY,
	PORTAL_STATUS
}
