package com.scai.customer_portal.dashboard;

import java.util.UUID;

/**
 * Cross-filter query params shared by slicer and chart APIs. All optional; omitted means "no filter".
 *
 * @param month        {@code YYYY-MM} — filters by {@code issueDate} within that calendar month
 * @param environment  use {@link DashboardCrossFilterSpecification#BLANK_TOKEN} for empty / null env in Jira
 * @param category     same {@code __BLANK__} convention for uncategorised issues
 * @param module       Jira/module string; {@code __BLANK__} for empty module
 */
public record DashboardFilterParams(
		UUID organizationId,
		UUID assigneeId,
		Integer severity,
		String environment,
		String month,
		RcaFilter rca,
		String category,
		String module,
		String jiraKey
) {
	public static DashboardFilterParams empty() {
		return new DashboardFilterParams(null, null, null, null, null, RcaFilter.ALL, null, null, null);
	}
}
