package com.scai.customer_portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.scai.customer_portal.service.IssueQuerySpec.State;
import com.scai.customer_portal.service.IssueQuerySpec.WindowApply;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Classifier {@code query} (and heuristics) → {@link IssueQuerySpec}.
 */
public final class IssueQuerySpecParser {

	private IssueQuerySpecParser() {
	}

	/**
	 * When the user combines high priority with time or “closed”, the first model may still say
	 * {@code high_priority}. We then treat the message as a filtered list.
	 */
	public static boolean shouldRerouteFromHighPriority(String userMessage) {
		if (userMessage == null || userMessage.isBlank()) {
			return false;
		}
		String m = userMessage.toLowerCase(Locale.ROOT);
		boolean hasPriority = m.contains("high")
				|| m.contains("highest")
				|| m.contains("priority")
				|| m.contains("severity")
				|| m.contains("sev");
		if (!hasPriority) {
			return false;
		}
		return m.contains("day")
				|| m.contains("din")
				|| m.contains("week")
				|| m.contains("month")
				|| m.contains("closed")
				|| m.contains("close")
				|| m.contains("band")
				|| m.contains("last ");
	}

	public static IssueQuerySpec parse(JsonNode q, String userMessage, boolean heuristicReroute) {
		if (heuristicReroute) {
			return heuristicsFromText(userMessage);
		}
		if (q == null || !q.isObject() || q.isEmpty()) {
			return heuristicsFromText(userMessage);
		}
		if (!q.has("window") && (q.has("priority") || q.has("state"))) {
			return mergeClassifierAndHeuristics(userMessage, text(q, "priority", "any"), text(q, "state", "any"));
		}
		return fromJsonObject(q, userMessage);
	}

	/** When model gave priority/state but not window — merge with time hints from the message. */
	private static IssueQuerySpec mergeClassifierAndHeuristics(
			String userMessage, String pr, String stS) {
		IssueQuerySpec h = heuristicsFromText(userMessage);
		Integer sev = mapPriority(pr, userMessage);
		if (sev == null) {
			sev = h.exactSeverity();
		}
		State st = mapState(stS);
		if (st == State.ANY) {
			st = h.state();
		}
		// if heuristics found a window, keep it with classifier priority/state
		if (h.hasTimeWindow()) {
			WindowApply wa = st == State.CLOSED
					? WindowApply.CLOSING
					: (st == State.OPEN ? WindowApply.ISSUE : h.windowApply());
			return new IssueQuerySpec(
					sev, true, h.rangeStart(), h.rangeEnd(), st, wa);
		}
		WindowApply w = st == State.CLOSED
				? WindowApply.CLOSING
				: (st == State.OPEN ? WindowApply.ISSUE : WindowApply.EITHER);
		return new IssueQuerySpec(sev, false, null, null, st, w);
	}

	private static IssueQuerySpec heuristicsFromText(String userMessage) {
		LocalDate today = LocalDate.now();
		if (userMessage == null) {
			return new IssueQuerySpec(null, false, null, null, State.ANY, WindowApply.NONE);
		}
		String m = userMessage.toLowerCase(Locale.ROOT);
		Integer sev = null;
		if (m.contains("highest")
				|| m.contains("critical")
				|| m.contains("sev 1")
				|| m.contains("severity 1")) {
			sev = 1;
		}
		if (m.contains("medium")
				|| m.contains("moderate")
				|| m.contains("sev 2")
				|| m.contains("severity 2")) {
			sev = 2;
		}
		if (m.contains("sev 3")
				|| m.contains("severity 3")
				|| (m.contains("low") && m.contains("priority"))) {
			sev = 3;
		}
		if (m.contains("high") && m.contains("priority") && sev == null) {
			sev = 1;
		}
		State st = State.ANY;
		if (m.contains("closed")
				|| m.contains("close")
				|| m.contains("band")
				|| m.contains("complete")) {
			if (!m.contains("still open") && !m.contains("reopen")) {
				st = State.CLOSED;
			}
		}
		if (m.contains("open ticket")
				|| m.contains("still open")
				|| (m.contains("unresolved") && m.contains("list"))) {
			if (!m.contains("close")) {
				st = State.OPEN;
			}
		}
		SummaryPeriod p = TimeWindowInference.strongTimeHint(userMessage).orElse(null);
		if (p == null) {
			if (m.contains("one day")
					|| m.contains("1 day")
					|| m.contains("ek din")
					|| m.contains("last 24")
					|| m.contains("last 24h")) {
				p = SummaryPeriod.LAST_1_DAY;
			}
			else if (m.contains("this week")
					|| m.contains("is week")
					|| m.contains("is hafte")) {
				p = SummaryPeriod.THIS_WEEK;
			}
			else if (m.contains("this month") || m.contains("is mahine") || m.contains("current month")) {
				p = SummaryPeriod.THIS_MONTH;
			}
			else if (m.contains("last month") || m.contains("pichhla") || m.contains("previous month")) {
				p = SummaryPeriod.LAST_MONTH;
			}
			else if (m.contains("7 day")
					|| m.contains("7 days")
					|| m.contains("last 7")) {
				p = SummaryPeriod.LAST_7_DAYS;
			}
		}
		ReportTimeWindows.DateRange dr = p != null ? ReportTimeWindows.rangeFor(p, today) : null;
		WindowApply wa = st == State.CLOSED
				? WindowApply.CLOSING
				: (st == State.OPEN ? WindowApply.ISSUE : WindowApply.EITHER);
		if (dr == null) {
			if (m.contains("close")
					&& (m.contains("one day")
					|| m.contains("1 day")
					|| m.contains("ek din")
					|| m.contains("last 24")
					|| m.contains("last 24h"))) {
				dr = ReportTimeWindows.rangeFor(SummaryPeriod.LAST_1_DAY, today);
			}
		}
		if (dr == null) {
			return new IssueQuerySpec(sev, false, null, null, st, WindowApply.NONE);
		}
		return new IssueQuerySpec(sev, true, dr.start(), dr.end(), st, wa);
	}

	private static IssueQuerySpec fromJsonObject(JsonNode q, String userMessage) {
		String pr = text(q, "priority", "any");
		String stS = text(q, "state", "any");
		String wS = text(q, "window", "all");
		String focus = text(q, "dateFocus", "auto");
		LocalDate today = LocalDate.now();
		Integer sev = mapPriority(pr, userMessage);
		State st = mapState(stS);
		if ("all".equalsIgnoreCase(wS)) {
			WindowApply wa0 = st == State.CLOSED
					? WindowApply.CLOSING
					: (st == State.OPEN ? WindowApply.ISSUE : WindowApply.EITHER);
			return new IssueQuerySpec(sev, false, null, null, st, wa0);
		}
		SummaryPeriod p = SummaryPeriod.fromModelToken(wS);
		if (p == SummaryPeriod.DEFAULT) {
			p = TimeWindowInference.strongTimeHint(userMessage)
					.or(() -> TimeWindowInference.softTimeHint(userMessage))
					.orElse(SummaryPeriod.LAST_7_DAYS);
		}
		ReportTimeWindows.DateRange dr = ReportTimeWindows.rangeFor(p, today);
		WindowApply wa = mapWindowApply(st, focus, userMessage);
		return new IssueQuerySpec(sev, true, dr.start(), dr.end(), st, wa);
	}

	private static WindowApply mapWindowApply(State st, String focus, String userMessage) {
		if ("issue".equalsIgnoreCase(focus) || "created".equalsIgnoreCase(focus)) {
			return WindowApply.ISSUE;
		}
		if ("closing".equalsIgnoreCase(focus) || "closed".equalsIgnoreCase(focus) || "resolution".equalsIgnoreCase(
				focus)) {
			return WindowApply.CLOSING;
		}
		if ("either".equalsIgnoreCase(focus)) {
			return WindowApply.EITHER;
		}
		if (st == State.CLOSED) {
			return WindowApply.CLOSING;
		}
		if (st == State.OPEN) {
			return WindowApply.ISSUE;
		}
		String m = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
		if (m.contains("close") && m.contains("day")) {
			return WindowApply.CLOSING;
		}
		return WindowApply.EITHER;
	}

	private static State mapState(String stS) {
		String s = stS == null ? "any" : stS.toLowerCase(Locale.ROOT);
		if (s.contains("open") && !s.equals("reopened")) {
			return State.OPEN;
		}
		if (s.contains("close") || s.contains("done") || s.contains("resolved")) {
			return State.CLOSED;
		}
		return State.ANY;
	}

	private static Integer mapPriority(String p, String userMessage) {
		if (p == null || p.isEmpty() || "any".equalsIgnoreCase(p)) {
			// from message
			String m = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
			if (m.contains("highest")
					|| m.contains("high priority")
					|| m.contains("sev 1")
					|| m.contains("severity 1")
					|| m.contains("critical")) {
				return 1;
			}
			if (m.contains("sev 2")
					|| m.contains("severity 2")
					|| m.contains("medium")
					|| m.contains("moderate")) {
				return 2;
			}
			if (m.contains("sev 3")
					|| m.contains("severity 3")
					|| m.contains("lowest")
					|| m.contains("low priority")) {
				return 3;
			}
			if (m.contains("low") && m.contains("priority")) {
				return 3;
			}
			if (m.contains("high") && m.contains("priority")) {
				return 1;
			}
			return null;
		}
		return switch (p.toLowerCase(Locale.ROOT)) {
			case "1", "highest", "high", "sev1", "critical" -> 1;
			case "2", "medium", "sev2", "moderate" -> 2;
			case "3", "low", "sev3", "lowest" -> 3;
			default -> null;
		};
	}

	private static String text(JsonNode n, String key, String def) {
		JsonNode c = n.get(key);
		if (c == null || c.isNull() || !c.isTextual()) {
			return def;
		}
		return c.asText("").trim();
	}
}
