package com.scai.customer_portal.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Turns Jira field JSON (string, option object, or array of options like customFieldOption) into display text.
 */
public final class JiraFieldValueTexts {

	private JiraFieldValueTexts() {
	}

	/**
	 * e.g. {@code [{"value":"Jockey",...}]} → {@code "Jockey"}; multiple options → comma-separated.
	 */
	public static String toDisplayString(JsonNode n) {
		if (n == null || n.isNull() || n.isMissingNode()) {
			return null;
		}
		if (n.isTextual()) {
			String t = n.asText().trim();
			return t.isEmpty() ? null : t;
		}
		if (n.isArray()) {
			if (n.isEmpty()) {
				return null;
			}
			StringBuilder sb = new StringBuilder();
			for (JsonNode item : n) {
				String part = singleNodeToText(item);
				if (part != null && !part.isBlank()) {
					if (!sb.isEmpty()) {
						sb.append(", ");
					}
					sb.append(part);
				}
			}
			return sb.isEmpty() ? null : sb.toString();
		}
		if (n.isObject() && "doc".equals(n.path("type").asText())) {
			String adf = adfDocumentToPlain(n);
			return adf != null && !adf.isBlank() ? adf.trim() : null;
		}
		return singleNodeToText(n);
	}

	private static String singleNodeToText(JsonNode n) {
		if (n == null || n.isNull() || n.isMissingNode()) {
			return null;
		}
		if (n.isTextual()) {
			String t = n.asText().trim();
			return t.isEmpty() ? null : t;
		}
		if (n.isObject()) {
			if (n.has("value")) {
				String v = n.path("value").asText(null);
				if (v != null && !v.isBlank()) {
					return v.trim();
				}
			}
			if (n.has("name")) {
				String nm = n.path("name").asText(null);
				if (nm != null && !nm.isBlank()) {
					return nm.trim();
				}
			}
			if (n.has("content")) {
				String adf = adfDocumentToPlain(n);
				if (adf != null && !adf.isBlank()) {
					return adf.trim();
				}
			}
		}
		return null;
	}

	/** Jira Cloud rich text (Atlassian Document Format) → plain text. */
	private static String adfDocumentToPlain(JsonNode node) {
		if (node == null || node.isNull() || node.isMissingNode()) {
			return null;
		}
		if (node.has("text") && node.path("text").isTextual()) {
			return node.path("text").asText("");
		}
		JsonNode content = node.get("content");
		if (content == null || !content.isArray() || content.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (JsonNode child : content) {
			String part = adfDocumentToPlain(child);
			if (part != null && !part.isEmpty()) {
				if (!sb.isEmpty()) {
					sb.append('\n');
				}
				sb.append(part);
			}
		}
		return sb.isEmpty() ? null : sb.toString();
	}
}
