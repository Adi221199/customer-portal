package com.scai.customer_portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.scai.customer_portal.config.JiraProperties;
import com.scai.customer_portal.domain.AppUser;
import com.scai.customer_portal.domain.Issue;
import com.scai.customer_portal.domain.IssueStatus;
import com.scai.customer_portal.domain.Organization;
import com.scai.customer_portal.domain.Pod;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@Component
public class JiraIssueMapper {

	private final JiraProperties jiraProperties;

	public JiraIssueMapper(JiraProperties jiraProperties) {
		this.jiraProperties = jiraProperties;
	}

	public void applyJsonToIssue(Issue target, JsonNode root, Organization organization, Pod pod, AppUser createdBy, String snapshotJson) {
		JsonNode fields = root.path("fields");
		String key = root.path("key").asText(null);
		String id = root.path("id").asText(null);
		target.setJiraIssueKey(key);
		target.setJiraIssueId(id);
		target.setTitle(fields.path("summary").asText("(no summary)"));
		target.setDescription(extractDescription(fields));
		target.setIssueDate(parseDate(fields.path("created").asText(null)));
		target.setClosingDate(parseDate(fields.path("resolutiondate").asText(null)));
		target.setJiraStatus(fields.path("status").path("name").asText(null));
		target.setPortalStatus(mapPortalStatus(target.getJiraStatus()));
		target.setSeverity(mapSeverity(fields.path("priority").path("name").asText(null)));
		String module = readCustomField(fields, jiraProperties.moduleFieldId());
		if (module == null || module.isBlank()) {
			module = joinComponentNames(fields.path("components"));
		}
		target.setModule(module);
		target.setEnvironment(readCustomField(fields, jiraProperties.environmentFieldId()));
		String category = readCustomField(fields, jiraProperties.categoryFieldId());
		if (category == null || category.isBlank()) {
			category = fields.path("issuetype").path("name").asText(null);
		}
		if (category == null || category.isBlank()) {
			category = joinLabels(fields.path("labels"));
		}
		target.setCategory(category);
		target.setOrganization(organization);
		target.setPod(pod);
		if (target.getCreatedBy() == null) {
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
		if (n == null || n.isNull()) {
			return null;
		}
		if (n.isTextual()) {
			return n.asText();
		}
		if (n.isObject() && n.has("value")) {
			return n.path("value").asText(null);
		}
		if (n.isObject() && n.has("name")) {
			return n.path("name").asText(null);
		}
		return n.toString();
	}

	private static String extractDescription(JsonNode fields) {
		JsonNode desc = fields.get("description");
		if (desc == null || desc.isNull()) {
			return null;
		}
		if (desc.isTextual()) {
			return desc.asText();
		}
		return desc.toString();
	}

	private static LocalDate parseDate(String iso) {
		if (iso == null || iso.isBlank()) {
			return null;
		}
		try {
			return OffsetDateTime.parse(iso).toLocalDate();
		}
		catch (DateTimeParseException e) {
			try {
				return Instant.parse(iso).atZone(java.time.ZoneOffset.UTC).toLocalDate();
			}
			catch (Exception ex) {
				return null;
			}
		}
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
