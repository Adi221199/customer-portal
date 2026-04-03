package com.scai.customer_portal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jira")
public record JiraProperties(
		String baseUrl,
		String email,
		String apiToken,
		String moduleFieldId,
		String environmentFieldId,
		String categoryFieldId
) {

	/** API token + Atlassian account email (integration user — not per portal end-user). */
	public boolean hasApiCredentials() {
		return email != null && !email.isBlank()
				&& apiToken != null && !apiToken.isBlank();
	}

	/** Issue import calls {@code jira.base-url}/rest/api/3/issue/{key} — base URL is required (key-only API). */
	public boolean hasBaseUrl() {
		return baseUrl != null && !baseUrl.isBlank();
	}

	public boolean isConfigured() {
		return hasApiCredentials() && hasBaseUrl();
	}
}
