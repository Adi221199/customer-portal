package com.scai.customer_portal.api;

import com.scai.customer_portal.api.dto.dashboard.DashboardAggregateResponse;
import com.scai.customer_portal.api.dto.dashboard.DashboardChartResponse;
import com.scai.customer_portal.api.dto.dashboard.DashboardFiltersResponse;
import com.scai.customer_portal.api.dto.dashboard.DashboardMetaResponse;
import com.scai.customer_portal.dashboard.DashboardAggregateDimension;
import com.scai.customer_portal.dashboard.DashboardChartId;
import com.scai.customer_portal.dashboard.DashboardFilterParams;
import com.scai.customer_portal.dashboard.RcaFilter;
import com.scai.customer_portal.service.DashboardService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

	private final DashboardService dashboardService;

	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@GetMapping("/meta")
	public DashboardMetaResponse meta() {
		return dashboardService.meta();
	}

	/**
	 * Distinct slicer values under current cross-filters (omit self per facet — Power BI behaviour).
	 */
	@GetMapping("/filters")
	public DashboardFiltersResponse filters(
			@RequestParam(required = false) UUID organizationId,
			@RequestParam(required = false) UUID assigneeId,
			@RequestParam(required = false) Integer severity,
			@RequestParam(required = false) String environment,
			@RequestParam(required = false) String month,
			@RequestParam(required = false) String rca,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) String module,
			@RequestParam(required = false) String jiraKey) {
		return dashboardService.filters(params(organizationId, assigneeId, severity, environment, month, rca, category, module, jiraKey));
	}

	/**
	 * One chart payload. Path segment e.g. {@code issues-by-month}. Pass same query params as filters for cross-filtering.
	 */
	@GetMapping("/charts/{chartPath}")
	public DashboardChartResponse chart(
			@PathVariable String chartPath,
			@RequestParam(required = false) UUID organizationId,
			@RequestParam(required = false) UUID assigneeId,
			@RequestParam(required = false) Integer severity,
			@RequestParam(required = false) String environment,
			@RequestParam(required = false) String month,
			@RequestParam(required = false) String rca,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) String module,
			@RequestParam(required = false) String jiraKey) {
		DashboardChartId chartId;
		try {
			chartId = DashboardChartId.fromPathSegment(chartPath);
		}
		catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
		}
		return dashboardService.chart(
				chartId,
				params(organizationId, assigneeId, severity, environment, month, rca, category, module, jiraKey));
	}

	/**
	 * Generic GROUP BY for decomposition tree: {@code groupBy=MONTH|CLIENT|ENVIRONMENT|SEVERITY|MODULE|CATEGORY|RCA}.
	 */
	@GetMapping("/aggregate")
	public DashboardAggregateResponse aggregate(
			@RequestParam String groupBy,
			@RequestParam(required = false) UUID organizationId,
			@RequestParam(required = false) UUID assigneeId,
			@RequestParam(required = false) Integer severity,
			@RequestParam(required = false) String environment,
			@RequestParam(required = false) String month,
			@RequestParam(required = false) String rca,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) String module,
			@RequestParam(required = false) String jiraKey) {
		DashboardAggregateDimension dim;
		try {
			dim = DashboardAggregateDimension.valueOf(groupBy.trim().toUpperCase());
		}
		catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid groupBy: " + groupBy);
		}
		return dashboardService.aggregate(dim, params(organizationId, assigneeId, severity, environment, month, rca, category, module, jiraKey));
	}

	private static DashboardFilterParams params(
			UUID organizationId,
			UUID assigneeId,
			Integer severity,
			String environment,
			String month,
			String rca,
			String category,
			String module,
			String jiraKey) {
		return new DashboardFilterParams(
				organizationId,
				assigneeId,
				severity,
				environment,
				month,
				RcaFilter.parse(rca),
				category,
				module,
				jiraKey);
	}
}
