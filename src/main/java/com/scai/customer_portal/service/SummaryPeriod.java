package com.scai.customer_portal.service;

import java.util.Locale;

/**
 * Period keywords produced by the chat intent classifier and parsed for ticket summaries.
 */
public enum SummaryPeriod {
	/** Today and yesterday (inclusive) — for “closed in one day / last 24h (date fields)”. */
	LAST_1_DAY,
	THIS_WEEK,
	LAST_7_DAYS,
	THIS_MONTH,
	LAST_30_DAYS,
	LAST_MONTH;

	/** Fallback when the model does not name a period and text inference also fails. */
	public static final SummaryPeriod DEFAULT = LAST_7_DAYS;

	public static SummaryPeriod fromModelToken(String raw) {
		if (raw == null || raw.isBlank()) {
			return DEFAULT;
		}
		String t = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
		return fromNormalizedToken(t);
	}

	/**
	 * Picks a report window: (1) strong phrase in the user message wins, (2) else non-default model token,
	 * (3) else soft text hint, (4) else {@link #DEFAULT}.
	 */
	public static SummaryPeriod forReport(String token, String userMessage) {
		java.util.Optional<SummaryPeriod> strong = TimeWindowInference.strongTimeHint(userMessage);
		if (strong.isPresent()) {
			return strong.get();
		}
		if (token != null && !token.isBlank()) {
			SummaryPeriod p = fromModelToken(token);
			if (p != DEFAULT) {
				return p;
			}
		}
		return TimeWindowInference.softTimeHint(userMessage).orElse(DEFAULT);
	}

	private static SummaryPeriod fromNormalizedToken(String t) {
		return switch (t) {
			case "last_1_day", "1d", "one_day", "today_yesterday" -> LAST_1_DAY;
			case "this_week", "week" -> THIS_WEEK;
			case "last_7_days", "7d", "last_week" -> LAST_7_DAYS;
			case "this_month" -> THIS_MONTH;
			case "last_30_days", "30d", "1_month", "one_month" -> LAST_30_DAYS;
			case "last_month", "previous_month" -> LAST_MONTH;
			default -> DEFAULT;
		};
	}
}
