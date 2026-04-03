package com.scai.customer_portal.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Send only the issue key (e.g. EDM-3617). Site URL is configured as {@code jira.base-url}.
 * Legacy JSON property {@code jiraKeyOrUrl} is still accepted as an alias.
 */
public record ImportJiraRequest(
		@NotBlank @Size(max = 64)
		@JsonProperty("jiraKey")
		@JsonAlias({ "jiraKeyOrUrl" })
		String jiraKey,
		UUID organizationId
) {
}
