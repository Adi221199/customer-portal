package com.scai.customer_portal.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Assistant reply plus optional dashboard cross-filters to apply on the client.
 */
public record ChatResponse(
		String reply,
		Map<String, List<String>> dashboardFilters,
		boolean replaceDashboardFilters,
		boolean navigateToDashboard
) {
	public static ChatResponse textOnly(String reply) {
		return new ChatResponse(reply, Map.of(), false, false);
	}
}
