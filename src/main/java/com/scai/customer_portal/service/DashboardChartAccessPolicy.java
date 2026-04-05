package com.scai.customer_portal.service;

import com.scai.customer_portal.dashboard.DashboardChartId;
import com.scai.customer_portal.domain.AppUser;

import java.util.Set;

/**
 * Extend or replace with a bean that loads per-user chart entitlements from DB.
 */
public interface DashboardChartAccessPolicy {

	Set<DashboardChartId> allowedCharts(AppUser user);

	void requireChart(AppUser user, DashboardChartId chartId);
}
