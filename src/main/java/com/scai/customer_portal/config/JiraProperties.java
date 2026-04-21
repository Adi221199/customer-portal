package com.scai.customer_portal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jira")
public record JiraProperties(
		String baseUrl,
		String email,
		String apiToken,
		String moduleFieldId,
		String environmentFieldId,
		String categoryFieldId,
		/** Optional: Jira custom field for RCA / root-cause text (plain or Atlassian Document Format). */
		String rcaFieldId,
		/**
		 * Optional AND fragment for {@code POST /api/issues/import-jira-bulk} JQL (e.g. {@code project in (EDM, DPAI)}).
		 * Appended to the non-empty Customer + Environment filter.
		 */
		String bulkImportExtraJql,
		/**
		 * Optional max issues from Jira search per bulk run. Unset, zero, or negative = no limit (all pages until Jira
		 * ends). Set a positive value to cap (e.g. staging).
		 */
		Integer bulkImportMaxTotal
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
