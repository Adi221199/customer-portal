package com.scai.customer_portal.api.dto.dashboard;

import com.scai.customer_portal.dashboard.DashboardAggregateDimension;

import java.util.List;

public record DashboardAggregateResponse(DashboardAggregateDimension groupBy, List<NameCountPoint> points) {
}
