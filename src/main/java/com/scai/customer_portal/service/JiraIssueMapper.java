package com.scai.customer_portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.scai.customer_portal.config.JiraProperties;
import com.scai.customer_portal.domain.AppUser;
import com.scai.customer_portal.domain.Issue;
import com.scai.customer_portal.domain.IssueStatus;
import com.scai.customer_portal.domain.Organization;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;

@Component
public class JiraIssueMapper {

	private final JiraProperties jiraProperties;
	private final JiraRemoteService jiraRemoteService;

	public JiraIssueMapper(JiraProperties jiraProperties, JiraRemoteService jiraRemoteService) {
		this.jiraProperties = jiraProperties;
		this.jiraRemoteService = jiraRemoteService;
	}

	public static String jiraUserEmail(JsonNode fields, String fieldKey) {
		return fields.path(fieldKey).path("emailAddress").asText(null);
	}

	/**
	 * Cron / background sync: status-like fields only. Does not touch customer/org, pod, env, module, category,
	 * RCA, reporter, assignee, description, issue date, etc., so portal values are not replaced by Jira nulls/omissions.
	 */
	public void applyBackgroundProgressFromJira(Issue target, JsonNode root, String snapshotJson) {
		JsonNode fields = root.path("fields");
		String key = root.path("key").asText(null);
		String id = root.path("id").asText(null);
		if (key != null && !key.isBlank()) {
			target.setJiraIssueKey(key);
		}
		if (id != null && !id.isBlank()) {
			target.setJiraIssueId(id);
		}
		String summary = fields.path("summary").asText(null);
		if (summary != null && !summary.isBlank()) {
			target.setTitle(summary.trim());
		}
		String jiraStatusName = fields.path("status").path("name").asText(null);
		target.setJiraStatus(jiraStatusName);
		target.setPortalStatus(mapPortalStatus(jiraStatusName));
		LocalDate closing = JiraDateParsing.parseLocalDate(JiraDateParsing.timestampText(fields, "resolutiondate"));
		if (closing != null) {
			target.setClosingDate(closing);
		}
		String priorityName = fields.path("priority").path("name").asText(null);
		if (priorityName != null && !priorityName.isBlank()) {
			target.setSeverity(mapSeverity(priorityName));
		}
		target.setLastSyncedAt(Instant.now());
		target.setJiraSnapshotJson(snapshotJson);
	}

	public void applyJsonToIssue(Issue target, JsonNode root, Organization organization, AppUser createdBy, String snapshotJson) {
		JsonNode fields = root.path("fields");
		String key = root.path("key").asText(null);
		String id = root.path("id").asText(null);
		target.setJiraIssueKey(key);
		target.setJiraIssueId(id);
		target.setTitle(fields.path("summary").asText("(no summary)"));
		target.setDescription(extractDescription(fields));
		LocalDate issueDate = JiraDateParsing.parseLocalDate(JiraDateParsing.timestampText(fields, "created"));
		if (issueDate == null) {
			issueDate = JiraDateParsing.parseLocalDate(JiraDateParsing.timestampText(fields, "updated"));
		}
		target.setIssueDate(issueDate);
		LocalDate closingDate = JiraDateParsing.parseLocalDate(JiraDateParsing.timestampText(fields, "resolutiondate"));
		target.setClosingDate(closingDate);
		target.setJiraStatus(fields.path("status").path("name").asText(null));
		target.setPortalStatus(mapPortalStatus(target.getJiraStatus()));
		target.setSeverity(mapSeverity(fields.path("priority").path("name").asText(null)));
		String module = readCustomField(fields, jiraProperties.moduleFieldId());
		if (module == null || module.isBlank()) {
			module = joinComponentNames(fields.path("components"));
		}
		target.setModule(module);
		target.setEnvironment(jiraRemoteService.extractEnvironmentValueFromFields(fields));
		String category = readCustomField(fields, jiraProperties.categoryFieldId());
		if (category == null || category.isBlank()) {
			category = fields.path("issuetype").path("name").asText(null);
		}
		if (category == null || category.isBlank()) {
			category = joinLabels(fields.path("labels"));
		}
		target.setCategory(category);
		target.setOrganization(organization);
		String rcaFieldId = jiraProperties.rcaFieldId();
		if (rcaFieldId != null && !rcaFieldId.isBlank()) {
			String fromJiraRca = readCustomField(fields, rcaFieldId);
			target.setRcaDescription(fromJiraRca != null && !fromJiraRca.isBlank() ? fromJiraRca.trim() : null);
		}
		JsonNode reporter = fields.path("reporter");
		if (!reporter.isMissingNode() && !reporter.isNull()) {
			target.setJiraReporterEmail(reporter.path("emailAddress").asText(null));
			target.setJiraReporterDisplayName(reporter.path("displayName").asText(null));
			target.setJiraReporterAccountId(reporter.path("accountId").asText(null));
		}
		if (target.getCreatedBy() == null && createdBy != null) {
			target.setCreatedBy(createdBy);
		}
		target.setLastSyncedAt(Instant.now());
		target.setJiraSnapshotJson(snapshotJson);
	}

	private static String joinComponentNames(JsonNode components) {
		if (components == null || !components.isArray() || components.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (JsonNode c : components) {
			String name = c.path("name").asText(null);
			if (name != null && !name.isBlank()) {
				if (!sb.isEmpty()) {
					sb.append(", ");
				}
				sb.append(name);
			}
		}
		return sb.isEmpty() ? null : sb.toString();
	}

	private static String joinLabels(JsonNode labels) {
		if (labels == null || !labels.isArray() || labels.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (JsonNode l : labels) {
			if (l.isTextual()) {
				String t = l.asText();
				if (t != null && !t.isBlank()) {
					if (!sb.isEmpty()) {
						sb.append(", ");
					}
					sb.append(t);
				}
			}
		}
		return sb.isEmpty() ? null : sb.toString();
	}

	private static String readCustomField(JsonNode fields, String fieldId) {
		if (fieldId == null || fieldId.isBlank()) {
			return null;
		}
		JsonNode n = fields.get(fieldId);
		return JiraFieldValueTexts.toDisplayString(n);
	}

	private static String extractDescription(JsonNode fields) {
		JsonNode desc = fields.get("description");
		if (desc == null || desc.isNull()) {
			return null;
		}
		if (desc.isTextual()) {
			return desc.asText();
		}
		String plain = JiraFieldValueTexts.toDisplayString(desc);
		if (plain != null && !plain.isBlank()) {
			return plain;
		}
		return desc.toString();
	}

	private static Integer mapSeverity(String priorityName) {
		if (priorityName == null) {
			return null;
		}
		String p = priorityName.toLowerCase();
		if (p.contains("highest") || p.contains("critical") || p.contains("blocker")) {
			return 1;
		}
		if (p.contains("high") || p.contains("major")) {
			return 1;
		}
		if (p.contains("medium")) {
			return 2;
		}
		if (p.contains("low") || p.contains("lowest") || p.contains("minor") || p.contains("trivial")) {
			return 3;
		}
		return 2;
	}

	private static IssueStatus mapPortalStatus(String jiraStatusName) {
		if (jiraStatusName == null) {
			return IssueStatus.OPEN;
		}
		String s = jiraStatusName.toLowerCase();
		if (s.contains("done") || s.contains("closed") || s.contains("resolved") || s.contains("complete")) {
			return IssueStatus.CLOSED;
		}
		if (s.contains("progress") || s.contains("development")) {
			return IssueStatus.IN_PROGRESS;
		}
		if (s.contains("ack")) {
			return IssueStatus.ACKNOWLEDGED;
		}
		if (s.contains("resolved")) {
			return IssueStatus.RESOLVED;
		}
		return IssueStatus.OPEN;
	}
}
