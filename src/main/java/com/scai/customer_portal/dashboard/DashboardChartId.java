package com.scai.customer_portal.dashboard;

/**
 * Stable ids for dashboard widgets — use for per-chart access control and API paths.
 */
public enum DashboardChartId {
	ISSUES_BY_MONTH,
	ISSUES_BY_CLIENT,
	ISSUES_BY_ENVIRONMENT,
	ISSUES_BY_SEVERITY,
	ISSUES_BY_MODULE,
	ISSUES_BY_TICKET_CATEGORY,
	ISSUES_BY_RCA,
	/** Generic drill-down / decomposition: use {@code GET /api/dashboard/aggregate}. */
	AGGREGATE;

	public String pathSegment() {
		return switch (this) {
			case ISSUES_BY_MONTH -> "issues-by-month";
			case ISSUES_BY_CLIENT -> "issues-by-client";
			case ISSUES_BY_ENVIRONMENT -> "issues-by-environment";
			case ISSUES_BY_SEVERITY -> "issues-by-severity";
			case ISSUES_BY_MODULE -> "issues-by-module";
			case ISSUES_BY_TICKET_CATEGORY -> "issues-by-ticket-category";
			case ISSUES_BY_RCA -> "issues-by-rca";
			case AGGREGATE -> "aggregate";
		};
	}

	public static DashboardChartId fromPathSegment(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("chart path required");
		}
		String s = raw.trim();
		for (DashboardChartId v : values()) {
			if (v.pathSegment().equalsIgnoreCase(s)) {
				return v;
			}
		}
		throw new IllegalArgumentException("Unknown chart: " + raw);
	}
}
