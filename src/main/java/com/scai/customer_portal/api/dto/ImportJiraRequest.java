package com.scai.customer_portal.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Send only the issue key (e.g. EDM-3617). Site URL is configured as {@code jira.base-url}.
 * Calling again for the same key updates the portal row from latest Jira data (same as {@code POST /api/issues/{id}/sync-from-jira}).
 * Legacy JSON property {@code jiraKeyOrUrl} is still accepted as an alias.
 * {@code organizationId} is ignored for 3SC internal import — org is taken from Jira Customer or defaults to "3SC".
 */
public record ImportJiraRequest(
		@NotBlank @Size(max = 64)
		@JsonProperty("jiraKey")
		@JsonAlias({ "jiraKeyOrUrl" })
		String jiraKey,
		@Deprecated
		UUID organizationId
) {
}
