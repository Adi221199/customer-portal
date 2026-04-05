package com.scai.customer_portal.service;

import com.scai.customer_portal.dashboard.DashboardChartId;
import com.scai.customer_portal.domain.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.Set;

@Component
public class PermissiveDashboardChartAccessPolicy implements DashboardChartAccessPolicy {

	@Override
	public Set<DashboardChartId> allowedCharts(AppUser user) {
		if (user == null) {
			return Set.of();
		}
		return EnumSet.allOf(DashboardChartId.class);
	}

	@Override
	public void requireChart(AppUser user, DashboardChartId chartId) {
		if (!allowedCharts(user).contains(chartId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No access to chart: " + chartId);
		}
	}
}
