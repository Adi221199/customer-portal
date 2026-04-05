package com.scai.customer_portal.api.dto.dashboard;

import com.scai.customer_portal.dashboard.DashboardChartId;

import java.util.List;

public record DashboardChartResponse(DashboardChartId chartId, List<NameCountPoint> points) {
}
