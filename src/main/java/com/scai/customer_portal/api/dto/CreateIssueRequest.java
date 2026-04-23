package com.scai.customer_portal.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Customer-raised issue (stored in the same {@code issues} table as Jira rows).
 */
public record CreateIssueRequest(
		@NotBlank @Size(max = 500) String title,
		@Size(max = 20000) String description,
		@Size(max = 200) String category,
		@Min(1) @Max(3) int priority,
		@Size(max = 64) String environment,
		@Size(max = 64) String module) {
}
