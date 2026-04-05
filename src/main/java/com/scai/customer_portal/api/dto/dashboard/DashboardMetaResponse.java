package com.scai.customer_portal.api.dto.dashboard;

import com.scai.customer_portal.dashboard.DashboardChartId;

import java.util.List;
import java.util.Set;

public record DashboardMetaResponse(
		List<DashboardChartMeta> charts,
		Set<DashboardChartId> allowedChartIds
) {
	public record DashboardChartMeta(
			DashboardChartId id,
			String pathSegment,
			String title,
			String description
	) {
	}
}
