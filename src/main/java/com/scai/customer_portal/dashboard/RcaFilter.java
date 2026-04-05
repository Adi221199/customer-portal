package com.scai.customer_portal.dashboard;

/**
 * Slicer for RCA text on issues. "NO" matches literal short answers stored as text (optional).
 */
public enum RcaFilter {
	ALL,
	HAS,
	EMPTY,
	NO;

	public static RcaFilter parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return ALL;
		}
		try {
			return valueOf(raw.trim().toUpperCase());
		}
		catch (IllegalArgumentException e) {
			return ALL;
		}
	}
}
