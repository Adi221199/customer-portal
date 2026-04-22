package com.scai.customer_portal.api.dto;

import java.util.List;
import java.util.Map;

/**
 * POST /api/chat — natural language assistant and dashboard filter hints.
 *
 * @param message                  user message (required)
 * @param currentDashboardFilters optional map of filter dimension → string values (same keys as portal dashboard)
 */
public record ChatRequest(String message, Map<String, List<String>> currentDashboardFilters) {
}
