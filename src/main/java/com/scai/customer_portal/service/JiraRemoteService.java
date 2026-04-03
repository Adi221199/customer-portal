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

@Service
public class JiraRemoteService {

	private final JiraProperties jiraProperties;
	private final ObjectMapper objectMapper;

	public JiraRemoteService(JiraProperties jiraProperties, ObjectMapper objectMapper) {
		this.jiraProperties = jiraProperties;
		this.objectMapper = objectMapper;
	}

	public boolean isConfigured() {
		return jiraProperties.isConfigured();
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
		String base = jiraProperties.baseUrl().trim().replaceAll("/+$", "");
		String auth = Base64.getEncoder().encodeToString(
				(jiraProperties.email() + ":" + jiraProperties.apiToken()).getBytes(StandardCharsets.UTF_8));
		RestClient client = RestClient.builder()
				.baseUrl(base)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.build();
		try {
			String body = client.get()
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
