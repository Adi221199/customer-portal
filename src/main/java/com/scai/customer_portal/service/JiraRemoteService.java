package com.scai.customer_portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scai.customer_portal.config.JiraProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class JiraRemoteService {

	private static final String CUSTOMER_FIELD_DISPLAY_NAME = "Customer";

	private final JiraProperties jiraProperties;
	private final ObjectMapper objectMapper;

	/**
	 * Lazily filled from a single {@code GET /rest/api/3/field} call: {@code ""} means "not found".
	 */
	private final AtomicReference<String> customerFieldIdCache = new AtomicReference<>();
	private final AtomicReference<String> environmentFieldIdCache = new AtomicReference<>();

	public JiraRemoteService(JiraProperties jiraProperties, ObjectMapper objectMapper) {
		this.jiraProperties = jiraProperties;
		this.objectMapper = objectMapper;
	}

	public boolean isConfigured() {
		return jiraProperties.isConfigured();
	}

	private RestClient authorizedClient() {
		String base = jiraProperties.baseUrl().trim().replaceAll("/+$", "");
		String auth = Base64.getEncoder().encodeToString(
				(jiraProperties.email() + ":" + jiraProperties.apiToken()).getBytes(StandardCharsets.UTF_8));
		return RestClient.builder()
				.baseUrl(base)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.build();
	}

	/**
	 * Finds Jira field whose display name is "Customer" (case-insensitive). Cached after first resolution.
	 *
	 * @return customfield_… id, or {@code null} if not found
	 */
	public String resolveCustomerFieldId() {
		ensureJiraFieldMetadataLoaded();
		String c = customerFieldIdCache.get();
		return c != null && !c.isBlank() ? c : null;
	}

	/**
	 * Jira UI often labels this "Env" or "Environment". Cached with Customer in one {@code GET /rest/api/3/field}.
	 */
	public String resolveEnvironmentFieldId() {
		ensureJiraFieldMetadataLoaded();
		String e = environmentFieldIdCache.get();
		return e != null && !e.isBlank() ? e : null;
	}

	private void ensureJiraFieldMetadataLoaded() {
		if (customerFieldIdCache.get() != null && environmentFieldIdCache.get() != null) {
			return;
		}
		synchronized (this) {
			if (customerFieldIdCache.get() != null && environmentFieldIdCache.get() != null) {
				return;
			}
			String customerId = "";
			String environmentId = "";
			JsonNode arr = fetchFieldListFromApi();
			if (arr != null && arr.isArray()) {
				for (JsonNode f : arr) {
					String name = f.path("name").asText("").trim();
					if (CUSTOMER_FIELD_DISPLAY_NAME.equalsIgnoreCase(name)) {
						String id = f.path("id").asText(null);
						if (id != null && !id.isBlank()) {
							customerId = id;
						}
						break;
					}
				}
				for (JsonNode f : arr) {
					String name = f.path("name").asText("").trim();
					if ("Env".equalsIgnoreCase(name)) {
						String id = f.path("id").asText(null);
						if (id != null && !id.isBlank()) {
							environmentId = id;
						}
						break;
					}
				}
				if (environmentId.isEmpty()) {
					for (JsonNode f : arr) {
						String name = f.path("name").asText("").trim();
						if ("Environment".equalsIgnoreCase(name)) {
							String id = f.path("id").asText(null);
							if (id != null && !id.isBlank()) {
								environmentId = id;
							}
							break;
						}
					}
				}
			}
			customerFieldIdCache.set(customerId);
			environmentFieldIdCache.set(environmentId);
		}
	}

	private JsonNode fetchFieldListFromApi() {
		if (!jiraProperties.hasApiCredentials() || !jiraProperties.hasBaseUrl()) {
			return null;
		}
		try {
			String body = authorizedClient().get()
					.uri("/rest/api/3/field")
					.retrieve()
					.body(String.class);
			JsonNode arr = objectMapper.readTree(body);
			if (!arr.isArray()) {
				return null;
			}
			return arr;
		}
		catch (RestClientException e) {
			throw new IllegalArgumentException("Failed to list Jira fields: " + e.getMessage(), e);
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to parse Jira field list", e);
		}
	}

	/**
	 * Reads Customer value from issue {@code fields} using auto-resolved field id.
	 */
	public String extractCustomerValueFromFields(JsonNode fields) {
		String fieldId = resolveCustomerFieldId();
		if (fieldId == null || fieldId.isBlank()) {
			return null;
		}
		return extractJiraFieldValue(fields, fieldId);
	}

	/**
	 * Environment / Env custom field: uses {@code jira.environment-field-id} when set, otherwise auto-detects
	 * field named "Env" or "Environment". Returns {@code null} when empty in Jira.
	 */
	public String extractEnvironmentValueFromFields(JsonNode fields) {
		String fieldId = jiraProperties.environmentFieldId();
		if (fieldId == null || fieldId.isBlank()) {
			fieldId = resolveEnvironmentFieldId();
		}
		if (fieldId == null || fieldId.isBlank()) {
			return null;
		}
		return extractJiraFieldValue(fields, fieldId);
	}

	/** Optional {@code jira.pod-field-id}: value should match a portal pod name (e.g. EDM, DevOps). */
	public String extractPodLabelFromFields(JsonNode fields) {
		String fieldId = jiraProperties.podFieldId();
		if (fieldId == null || fieldId.isBlank()) {
			return null;
		}
		return extractJiraFieldValue(fields, fieldId);
	}

	static String extractJiraFieldValue(JsonNode fields, String fieldId) {
		if (fields == null || fields.isMissingNode() || fieldId == null || fieldId.isBlank()) {
			return null;
		}
		JsonNode n = fields.get(fieldId);
		return JiraFieldValueTexts.toDisplayString(n);
	}

	/**
	 * @param jiraKeyOrKeyLikeString issue key only (e.g. {@code EDM-3617}); if a URL is pasted, only the key is used.
	 *        REST base URL is always {@code jira.base-url}.
	 */
	public JsonNode fetchIssue(String jiraKeyOrKeyLikeString) {
		if (!jiraProperties.hasApiCredentials()) {
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"Jira API credentials missing: set jira.email and jira.api-token. "
							+ "Use an API token from id.atlassian.com (Jira does not accept account passwords for REST API).");
		}
		if (!jiraProperties.hasBaseUrl()) {
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"jira.base-url is not set (e.g. https://3scsolution.atlassian.net for key-only imports).");
		}
		String issueKey = JiraIssueKeyParser.parse(jiraKeyOrKeyLikeString);
		try {
			String body = authorizedClient().get()
					.uri("/rest/api/3/issue/{key}", issueKey)
					.retrieve()
					.body(String.class);
			return objectMapper.readTree(body);
		}
		catch (RestClientException e) {
			throw new IllegalArgumentException("Failed to fetch Jira issue: " + e.getMessage(), e);
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to parse Jira response", e);
		}
	}
}
