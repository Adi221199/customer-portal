package com.scai.customer_portal.dashboard;

import com.scai.customer_portal.domain.IssueStatus;

import java.util.List;
import java.util.UUID;

/**
 * Cross-filter query params for dashboard GETs. {@code null} or empty list = no filter on that dimension.
 * Repeat the same query key for multi-select (OR within the dimension), e.g. {@code organizationId=a&organizationId=b}.
 */
public record DashboardFilterParams(
		List<UUID> organizationIds,
		List<UUID> assigneeIds,
		List<Integer> severities,
		List<String> environments,
		List<String> months,
		List<RcaFilter> rcaFilters,
		List<String> categories,
		List<String> modules,
		List<String> jiraKeys,
		List<IssueStatus> portalStatuses
) {
	public static DashboardFilterParams empty() {
		return new DashboardFilterParams(null, null, null, null, null, null, null, null, null, null);
	}
}
