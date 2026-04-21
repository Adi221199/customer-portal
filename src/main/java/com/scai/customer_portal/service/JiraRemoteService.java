package com.scai.customer_portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scai.customer_portal.api.dto.BulkJiraImportRequest;
import com.scai.customer_portal.config.JiraProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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

	private final ConcurrentHashMap<String, Optional<String>> jiraUserEmailByAccountIdCache = new ConcurrentHashMap<>();

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

	/** Same field id logic as {@link #extractEnvironmentValueFromFields(JsonNode)} but for JQL / search. */
	private String environmentFieldIdForSearch() {
		String fieldId = jiraProperties.environmentFieldId();
		if (fieldId != null && !fieldId.isBlank()) {
			return fieldId.trim();
		}
		return resolveEnvironmentFieldId();
	}

	/**
	 * Max issues to enumerate in one bulk-import JQL search (see {@code jira.bulk-import-max-total}).
	 * {@code null}, zero, or negative = no cap (paginate until Jira returns no more matches).
	 */
	public int configuredBulkImportMaxTotal() {
		Integer m = jiraProperties.bulkImportMaxTotal();
		if (m == null || m <= 0) {
			return Integer.MAX_VALUE;
		}
		return m;
	}

	/**
	 * Best-effort: Jira often omits {@code emailAddress} on embedded users; {@code GET /rest/api/3/user} may return it
	 * when the integration account can browse users. Cached per account id.
	 */
	public String lookupUserEmailByAccountId(String accountId) {
		if (accountId == null || accountId.isBlank() || !jiraProperties.hasApiCredentials() || !jiraProperties.hasBaseUrl()) {
			return null;
		}
		return jiraUserEmailByAccountIdCache.computeIfAbsent(accountId.trim(), this::fetchUserEmailOnce)
				.orElse(null);
	}

	private Optional<String> fetchUserEmailOnce(String accountId) {
		try {
			String body = authorizedClient().get()
					.uri("/rest/api/3/user?accountId={aid}", accountId)
					.retrieve()
					.body(String.class);
			JsonNode n = objectMapper.readTree(body);
			String email = n.path("emailAddress").asText(null);
			if (email != null && !email.isBlank()) {
				return Optional.of(email.trim());
			}
			return Optional.empty();
		}
		catch (Exception e) {
			return Optional.empty();
		}
	}

	/**
	 * Builds JQL for bulk import: optional equality on Customer / Environment / projects, optional strict
	 * non-empty Customer+Environment, optional {@code jira.bulk-import-extra-jql}.
	 */
	public String buildBulkImportJql(BulkJiraImportRequest filter) {
		if (!jiraProperties.hasApiCredentials() || !jiraProperties.hasBaseUrl()) {
			throw new IllegalArgumentException("Jira is not configured (base URL and API credentials required)");
		}
		String customerFieldId = resolveCustomerFieldId();
		String envFieldId = environmentFieldIdForSearch();
		boolean strict = filter.strictCustomerAndEnv();
		if (strict) {
			if (customerFieldId == null || customerFieldId.isBlank()) {
				throw new IllegalArgumentException(
						"Could not resolve Jira Customer field id (expected a field named Customer).");
			}
			if (envFieldId == null || envFieldId.isBlank()) {
				throw new IllegalArgumentException(
						"Could not resolve Environment field id: set jira.environment-field-id or add Env/Environment in Jira.");
			}
		}
		else {
			boolean narrowedByProject = filter.projectKeys() != null && !filter.projectKeys().isEmpty();
			boolean narrowedByCustomer = filter.customerNames() != null && !filter.customerNames().isEmpty();
			boolean narrowedByEnv = filter.environments() != null && !filter.environments().isEmpty();
			String extraProp = jiraProperties.bulkImportExtraJql();
			boolean narrowedByProp = extraProp != null && !extraProp.isBlank();
			if (!narrowedByProject && !narrowedByCustomer && !narrowedByEnv && !narrowedByProp) {
				throw new IllegalArgumentException(
						"When requireCustomerAndEnvironmentNonEmpty is false, specify projectKeys, customerNames, environments, and/or jira.bulk-import-extra-jql.");
			}
			if (narrowedByCustomer && (customerFieldId == null || customerFieldId.isBlank())) {
				throw new IllegalArgumentException("Could not resolve Jira Customer field id for JQL.");
			}
			if (narrowedByEnv && (envFieldId == null || envFieldId.isBlank())) {
				throw new IllegalArgumentException("Could not resolve Environment field id for JQL.");
			}
		}
		List<String> clauses = new ArrayList<>();
		if (strict && customerFieldId != null && envFieldId != null) {
			clauses.add(customerFieldId + " is not EMPTY");
			clauses.add(envFieldId + " is not EMPTY");
		}
		if (filter.customerNames() != null && !filter.customerNames().isEmpty()) {
			if (customerFieldId == null || customerFieldId.isBlank()) {
				throw new IllegalArgumentException("Could not resolve Jira Customer field id for JQL.");
			}
			List<String> names = filter.customerNames().stream()
					.map(String::trim)
					.filter(s -> !s.isBlank())
					.distinct()
					.toList();
			if (!names.isEmpty()) {
				String orExpr = names.stream()
						.map(JqlLiterals::quotedString)
						.map(q -> customerFieldId + " = " + q)
						.reduce((a, b) -> a + " OR " + b)
						.orElse("");
				clauses.add("(" + orExpr + ")");
			}
		}
		if (filter.environments() != null && !filter.environments().isEmpty()) {
			if (envFieldId == null || envFieldId.isBlank()) {
				throw new IllegalArgumentException("Environment filter set but Environment field id is unknown.");
			}
			List<String> envs = filter.environments().stream()
					.map(String::trim)
					.filter(s -> !s.isBlank())
					.distinct()
					.toList();
			if (!envs.isEmpty()) {
				String orExpr = envs.stream()
						.map(JqlLiterals::quotedString)
						.map(q -> envFieldId + " = " + q)
						.reduce((a, b) -> a + " OR " + b)
						.orElse("");
				clauses.add("(" + orExpr + ")");
			}
		}
		if (filter.projectKeys() != null && !filter.projectKeys().isEmpty()) {
			List<String> keys = filter.projectKeys().stream()
					.map(String::trim)
					.filter(s -> !s.isBlank())
					.map(s -> s.replaceAll("[^A-Za-z0-9_]", "").toUpperCase(Locale.ROOT))
					.filter(s -> !s.isBlank() && s.length() <= 32)
					.distinct()
					.toList();
			if (!keys.isEmpty()) {
				clauses.add("project in (" + String.join(", ", keys) + ")");
			}
		}
		String extra = jiraProperties.bulkImportExtraJql();
		if (extra != null && !extra.isBlank()) {
			clauses.add("(" + extra.trim() + ")");
		}
		if (clauses.isEmpty()) {
			throw new IllegalArgumentException("No JQL clauses built for bulk import.");
		}
		return String.join(" AND ", clauses) + " ORDER BY key ASC";
	}

	/**
	 * {@code POST /rest/api/3/search/jql} in pages; returns issue keys only (full issue is still fetched per key).
	 */
	public List<String> searchIssueKeysForBulkImport(BulkJiraImportRequest filter, int maxTotal) {
		int cap = maxTotal <= 0 ? Integer.MAX_VALUE : maxTotal;
		String jql = buildBulkImportJql(filter);
		return searchIssueKeysByJql(jql, cap);
	}

	private List<String> searchIssueKeysByJql(String jql, int maxTotal) {
		List<String> keys = new ArrayList<>();
		final int pageCap = 100;
		String nextPageToken = null;
		try {
			while (keys.size() < maxTotal) {
				int remaining = maxTotal - keys.size();
				int pageSize = Math.min(pageCap, Math.max(1, remaining));
				ObjectNode req = objectMapper.createObjectNode();
				req.put("jql", jql);
				req.put("maxResults", pageSize);
				req.putArray("fields").add("key");
				if (nextPageToken != null && !nextPageToken.isBlank()) {
					req.put("nextPageToken", nextPageToken);
				}
				String payload = objectMapper.writeValueAsString(req);
				String resp = authorizedClient().post()
						.uri("/rest/api/3/search/jql")
						.contentType(MediaType.APPLICATION_JSON)
						.body(payload)
						.retrieve()
						.body(String.class);
				JsonNode root = objectMapper.readTree(resp);
				JsonNode issues = root.path("issues");
				if (!issues.isArray() || issues.isEmpty()) {
					break;
				}
				for (JsonNode issue : issues) {
					String key = issue.path("key").asText(null);
					if (key != null && !key.isBlank()) {
						keys.add(key);
						if (keys.size() >= maxTotal) {
							break;
						}
					}
				}
				if (keys.size() >= maxTotal) {
					break;
				}
				if (root.path("isLast").asBoolean(false)) {
					break;
				}
				nextPageToken = root.path("nextPageToken").asText(null);
				if (nextPageToken == null || nextPageToken.isBlank()) {
					break;
				}
			}
		}
		catch (RestClientException e) {
			throw new IllegalArgumentException("Jira search failed: " + e.getMessage(), e);
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Jira search failed: " + e.getMessage(), e);
		}
		return keys;
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
