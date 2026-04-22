package com.scai.customer_portal.service;

import java.util.Locale;
import java.util.Optional;

/**
 * Derives a {@link SummaryPeriod} from free text when the classifier token is missing or too generic. Strong
 * matches (e.g. “one day”, “ek din”) override so user wording wins over a wrong model token.
 */
public final class TimeWindowInference {

	private TimeWindowInference() {
	}

	/**
	 * Strong signals: user clearly named a window. Checked before the model’s {@code summaryPeriod} token.
	 */
	public static Optional<SummaryPeriod> strongTimeHint(String userMessage) {
		if (userMessage == null || userMessage.isBlank()) {
			return Optional.empty();
		}
		String m = userMessage.toLowerCase(Locale.ROOT);
		if (m.contains("one day")
				|| m.contains("1 day")
				|| m.contains("ek din")
				|| m.contains("last 24")
				|| m.contains("last 24h")
				|| m.contains("aaj")
				|| m.contains("kal")
				|| (m.contains("close") && (m.contains(" in a day") || m.contains(" in one day") || m.contains("one day")))) {
			return Optional.of(SummaryPeriod.LAST_1_DAY);
		}
		if (m.contains("last month") || m.contains("pichhla") || m.contains("previous month")) {
			return Optional.of(SummaryPeriod.LAST_MONTH);
		}
		if (m.contains("this month") || m.contains("is mahine") || m.contains("current month")) {
			return Optional.of(SummaryPeriod.THIS_MONTH);
		}
		if (m.contains("this week") || m.contains("is week") || m.contains("is hafte")) {
			return Optional.of(SummaryPeriod.THIS_WEEK);
		}
		if (m.contains("last 7") || m.contains("7 days") || m.contains("past 7") || m.contains("past week")) {
			return Optional.of(SummaryPeriod.LAST_7_DAYS);
		}
		if (m.contains("30 days")
				|| m.contains("last 30")
				|| m.contains("monthly report")
				|| (m.contains("one month") && m.contains("last"))
				|| m.contains("last 30 day")) {
			return Optional.of(SummaryPeriod.LAST_30_DAYS);
		}
		return Optional.empty();
	}

	/**
	 * Weaker signals when the model did not return a useful token and strong hints did not fire.
	 */
	public static Optional<SummaryPeriod> softTimeHint(String userMessage) {
		if (userMessage == null || userMessage.isBlank()) {
			return Optional.empty();
		}
		String m = userMessage.toLowerCase(Locale.ROOT);
		if (m.contains("week") && !m.contains("month")) {
			return Optional.of(SummaryPeriod.THIS_WEEK);
		}
		if (m.contains("month") && (m.contains("this") || m.contains("current"))) {
			return Optional.of(SummaryPeriod.THIS_MONTH);
		}
		if (m.contains("month") && m.contains("last")) {
			return Optional.of(SummaryPeriod.LAST_MONTH);
		}
		if (m.contains("7") && m.contains("day")) {
			return Optional.of(SummaryPeriod.LAST_7_DAYS);
		}
		return Optional.empty();
	}
}
