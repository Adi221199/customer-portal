package com.scai.customer_portal.service;

/**
 * Escape values for Jira JQL string literals.
 */
public final class JqlLiterals {

	private JqlLiterals() {
	}

	public static String quotedString(String raw) {
		if (raw == null) {
			throw new IllegalArgumentException("JQL value is null");
		}
		String t = raw.trim().replace("\\", "\\\\").replace("\"", "\\\"");
		return "\"" + t + "\"";
	}
}
