package com.scai.customer_portal.api;

import com.scai.customer_portal.api.dto.dashboard.DashboardAggregateResponse;
import com.scai.customer_portal.api.dto.dashboard.DashboardChartResponse;
import com.scai.customer_portal.api.dto.dashboard.DashboardFiltersResponse;
import com.scai.customer_portal.api.dto.dashboard.DashboardMetaResponse;
import com.scai.customer_portal.dashboard.DashboardAggregateDimension;
import com.scai.customer_portal.dashboard.DashboardChartId;
import com.scai.customer_portal.dashboard.DashboardFilterParams;
import com.scai.customer_portal.dashboard.RcaFilter;
import com.scai.customer_portal.domain.IssueStatus;
import com.scai.customer_portal.service.DashboardService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
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
	 * Slicer values under current cross-filters. Pass the <strong>same</strong> query params as chart/aggregate calls
	 * (including chart drill: e.g. {@code month=2026-04} from a bar key). Each facet omits only its own filter when
	 * building its list so other slicers stay fully narrowed by all selections.
	 */
	@GetMapping("/filters")
	public DashboardFiltersResponse filters(
			@RequestParam(required = false) List<UUID> organizationId,
			@RequestParam(required = false) List<UUID> assigneeId,
			@RequestParam(required = false) List<String> assigneeEmail,
			@RequestParam(required = false) List<Integer> severity,
			@RequestParam(required = false) List<String> environment,
			@RequestParam(required = false) List<String> month,
			@RequestParam(required = false) List<String> rca,
			@RequestParam(required = false) List<String> category,
			@RequestParam(required = false) List<String> module,
			@RequestParam(required = false) List<String> jiraKey,
			@RequestParam(required = false) List<String> portalStatus) {
		return dashboardService.filters(params(
				organizationId, assigneeId, assigneeEmail, severity, environment, month, rca, category, module, jiraKey, portalStatus));
	}

	@GetMapping("/charts/{chartPath}")
	public DashboardChartResponse chart(
			@PathVariable String chartPath,
			@RequestParam(required = false) List<UUID> organizationId,
			@RequestParam(required = false) List<UUID> assigneeId,
			@RequestParam(required = false) List<String> assigneeEmail,
			@RequestParam(required = false) List<Integer> severity,
			@RequestParam(required = false) List<String> environment,
			@RequestParam(required = false) List<String> month,
			@RequestParam(required = false) List<String> rca,
			@RequestParam(required = false) List<String> category,
			@RequestParam(required = false) List<String> module,
			@RequestParam(required = false) List<String> jiraKey,
			@RequestParam(required = false) List<String> portalStatus) {
		DashboardChartId chartId;
		try {
			chartId = DashboardChartId.fromPathSegment(chartPath);
		}
		catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
		}
		return dashboardService.chart(
				chartId,
				params(organizationId, assigneeId, assigneeEmail, severity, environment, month, rca, category, module, jiraKey, portalStatus));
	}

	@GetMapping("/aggregate")
	public DashboardAggregateResponse aggregate(
			@RequestParam String groupBy,
			@RequestParam(required = false) List<UUID> organizationId,
			@RequestParam(required = false) List<UUID> assigneeId,
			@RequestParam(required = false) List<String> assigneeEmail,
			@RequestParam(required = false) List<Integer> severity,
			@RequestParam(required = false) List<String> environment,
			@RequestParam(required = false) List<String> month,
			@RequestParam(required = false) List<String> rca,
			@RequestParam(required = false) List<String> category,
			@RequestParam(required = false) List<String> module,
			@RequestParam(required = false) List<String> jiraKey,
			@RequestParam(required = false) List<String> portalStatus) {
		DashboardAggregateDimension dim;
		try {
			dim = DashboardAggregateDimension.valueOf(groupBy.trim().toUpperCase());
		}
		catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid groupBy: " + groupBy);
		}
		return dashboardService.aggregate(dim, params(
				organizationId, assigneeId, assigneeEmail, severity, environment, month, rca, category, module, jiraKey, portalStatus));
	}

	private static DashboardFilterParams params(
			List<UUID> organizationId,
			List<UUID> assigneeId,
			List<String> assigneeEmail,
			List<Integer> severity,
			List<String> environment,
			List<String> month,
			List<String> rca,
			List<String> category,
			List<String> module,
			List<String> jiraKey,
			List<String> portalStatus) {
		return new DashboardFilterParams(
				nullIfEmpty(organizationId),
				nullIfEmpty(assigneeId),
				nullIfEmpty(trimNonBlank(assigneeEmail)),
				nullIfEmpty(filterNulls(severity)),
				nullIfEmpty(trimNonBlank(environment)),
				nullIfEmpty(trimNonBlank(month)),
				normalizeRca(rca),
				nullIfEmpty(trimNonBlank(category)),
				nullIfEmpty(trimNonBlank(module)),
				nullIfEmpty(trimNonBlank(jiraKey)),
				parsePortalStatuses(portalStatus));
	}

	private static List<IssueStatus> parsePortalStatuses(List<String> raw) {
		if (raw == null || raw.isEmpty()) {
			return null;
		}
		List<IssueStatus> out = new ArrayList<>();
		for (String s : raw) {
			if (s == null || s.isBlank()) {
				continue;
			}
			try {
				out.add(IssueStatus.valueOf(s.trim().toUpperCase()));
			}
			catch (IllegalArgumentException ignored) {
				// skip unknown
			}
		}
		return out.isEmpty() ? null : out;
	}

	private static List<Integer> filterNulls(List<Integer> list) {
		if (list == null) {
			return null;
		}
		return list.stream().filter(s -> s != null).toList();
	}

	private static List<String> trimNonBlank(List<String> list) {
		if (list == null) {
			return null;
		}
		List<String> out = list.stream()
				.map(s -> s == null ? "" : s.trim())
				.filter(s -> !s.isBlank())
				.toList();
		return out.isEmpty() ? null : out;
	}

	private static List<RcaFilter> normalizeRca(List<String> raw) {
		if (raw == null || raw.isEmpty()) {
			return null;
		}
		List<RcaFilter> parsed = raw.stream().map(RcaFilter::parse).filter(r -> r != RcaFilter.ALL).distinct().toList();
		return parsed.isEmpty() ? null : parsed;
	}

	private static <T> List<T> nullIfEmpty(List<T> list) {
		return (list == null || list.isEmpty()) ? null : list;
	}
}
