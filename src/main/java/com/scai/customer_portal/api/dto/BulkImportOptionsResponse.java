package com.scai.customer_portal.api.dto;

import java.util.List;

/** Options for building bulk-import filters in the UI (internal roles). */
public record BulkImportOptionsResponse(
		List<NameLabelOption> organizations,
		List<NameLabelOption> pods,
		List<String> environments
) {
	public record NameLabelOption(String value, String label) {
	}
}
