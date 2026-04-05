package com.scai.customer_portal.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Gap-fill patch: each property applies only if the issue value is still empty (see service rules). */
public record IssuePatchRequest(
		LocalDate closingDate,
		@Size(max = 500) String module,
		@Size(max = 200) String environment,
		@Size(max = 200) String category,
		@Min(1) @Max(3) Integer severity,
		String rcaDescription,
		@Size(max = 200) String organizationName,
		@Size(max = 200) String podName
) {
}
