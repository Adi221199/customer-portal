package com.scai.customer_portal.api.dto.dashboard;

import java.util.List;

public record DashboardFiltersResponse(
		List<OrganizationOption> clients,
		List<AssigneeOption> deliverySpocs,
		List<Integer> severities,
		List<String> environments,
		List<String> months,
		List<String> rcaOptions,
		List<String> ticketCategories,
		List<String> modules,
		List<JiraTicketOption> jiraTickets,
		/** Distinct portal status enum names (OPEN, IN_PROGRESS, …) under current cross-filters. */
		List<String> issueStatuses
) {
}
