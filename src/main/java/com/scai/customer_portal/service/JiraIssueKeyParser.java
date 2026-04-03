package com.scai.customer_portal.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts issue key (e.g. EDM-3617) from bare key or accidental paste containing a browse URL.
 * Site URL always comes from {@code jira.base-url} — not from the request body.
 */
public final class JiraIssueKeyParser {

	private static final Pattern ISSUE_KEY = Pattern.compile("([A-Z][A-Z0-9]+-\\d+)");

	private JiraIssueKeyParser() {
	}

	public static String parse(String input) {
		if (input == null || input.isBlank()) {
			throw new IllegalArgumentException("Jira issue key is required (e.g. EDM-3617)");
		}
		Matcher m = ISSUE_KEY.matcher(input.trim());
		if (!m.find()) {
			throw new IllegalArgumentException("Could not parse a Jira issue key (expected e.g. EDM-3617)");
		}
		return m.group(1);
	}
}
