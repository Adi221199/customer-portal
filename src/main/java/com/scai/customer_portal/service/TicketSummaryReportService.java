package com.scai.customer_portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scai.customer_portal.domain.Issue;
import com.scai.customer_portal.repository.IssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds a single JSON document with ticket statistics for the chat assistant. All figures are
 * derived in Java from issues visible to the current user.
 */
@Service
public class TicketSummaryReportService {

	private static final int MAX_TICKETS_IN_JSON = 400;

	private final IssueRepository issueRepository;
	private final CurrentUserService currentUserService;
	private final ObjectMapper objectMapper;

	public TicketSummaryReportService(
			IssueRepository issueRepository,
			CurrentUserService currentUserService,
			ObjectMapper objectMapper) {
		this.issueRepository = issueRepository;
		this.currentUserService = currentUserService;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public String buildReportJson(SummaryPeriod period) {

		List<Issue> issues = issueRepository.findAll(
				IssueVisibilitySpecification.visibleTo(currentUserService.requireCurrentUser())
		);

		ReportTimeWindows.DateRange range =
				ReportTimeWindows.rangeFor(period, LocalDate.now());

		// ✅ STEP 1: FILTER DATA BASED ON RANGE
		List<Issue> filteredIssues = issues.stream()
				.filter(i -> {
					boolean createdInRange =
							i.getIssueDate() != null && inRange(i.getIssueDate(), range);

					boolean closedInRange =
							i.getClosingDate() != null && inRange(i.getClosingDate(), range);

					return createdInRange || closedInRange;
				})
				.toList();

		ReportModel m = new ReportModel();

		m.put("timePeriod", Map.of(
				"label", range.label(),
				"start", range.start().toString(),
				"end", range.end().toString(),
				"periodKey", period.name()
		));

		// ✅ STEP 2: OVERALL (NOW CORRECT)
		int n = filteredIssues.size();
		int open = 0;
		int closed = 0;
		int high = 0;
		int med = 0;
		int low = 0;
		int sevUnk = 0;

		List<String> blockedIds = new ArrayList<>();

		for (Issue i : filteredIssues) {

			if (IssueProgressUtil.isJiraBlocked(i)) {
				String k = i.getJiraIssueKey();
				if (k != null && !k.isBlank()) {
					blockedIds.add(k);
				}
			}

			if (IssueProgressUtil.isEffectivelyClosed(i)) {
				closed++;
			} else {
				open++;
			}

			Integer s = i.getSeverity();
			if (s == null) {
				sevUnk++;
			} else if (s == 1) {
				high++;
			} else if (s == 2) {
				med++;
			} else {
				low++;
			}
		}

		double closureRate = n == 0 ? 0.0 : Math.round(1000.0 * closed / n) / 10.0;

		m.put("overall", Map.of(
				"totalTickets", n,
				"openTickets", open,
				"closedTickets", closed,
				"closureRatePercent", closureRate
		));

		// ✅ STEP 3: ACTIVITY (ALREADY CORRECT)
		int created = 0;
		int closedInP = 0;

		for (Issue i : filteredIssues) {

			if (i.getIssueDate() != null && inRange(i.getIssueDate(), range)) {
				created++;
			}

			if (i.getClosingDate() != null &&
					inRange(i.getClosingDate(), range) &&
					IssueProgressUtil.isEffectivelyClosed(i)) {
				closedInP++;
			}
		}

		int net = created - closedInP;

		String trendHint = n == 0
				? "No issues in this period."
				: (created + closedInP) == 0
				? "No activity in this period."
				: String.format(Locale.ROOT,
				"In this period: %d created, %d closed. Net %s%d.",
				created, closedInP, net >= 0 ? "+" : "", net);

		m.put("activity", Map.of(
				"ticketsCreated", created,
				"ticketsClosed", closedInP,
				"trendHint", trendHint
		));

		// ✅ STEP 4: PRIORITY (NOW FILTERED)
		m.put("priorityBreakdown", Map.of(
				"high", high,
				"medium", med,
				"low", low,
				"unspecified", sevUnk
		));

		// ✅ STEP 5: BLOCKED
		m.put("blockedTicketIds", blockedIds.stream().sorted().toList());

		// ✅ STEP 6: ASSIGNEE PERFORMANCE (USE FILTERED LIST)
		m.put("assigneeClosedInPeriod", assigneeClosedInRange(filteredIssues, range));

		String top = topAssigneeLine(m.getAssigneeMap());
		m.put("topPerformerHint", top);

		// ✅ STEP 7: TICKET LIST (FILTERED)
		m.put("tickets", toTicketRows(filteredIssues, range));
		m.put("truncatedTicketList", filteredIssues.size() > MAX_TICKETS_IN_JSON);

		try {
			return objectMapper
					.writerWithDefaultPrettyPrinter()
					.writeValueAsString(m.map());
		} catch (Exception e) {
			throw new IllegalStateException("Failed to serialize report", e);
		}
	}

//	@Transactional(readOnly = true)
//	public String buildReportJson(SummaryPeriod period) {
//		List<Issue> issues = issueRepository.findAll(IssueVisibilitySpecification.visibleTo(currentUserService.requireCurrentUser()));
//		ReportTimeWindows.DateRange range = ReportTimeWindows.rangeFor(period, LocalDate.now());
//		ReportModel m = new ReportModel();
//		m.put("timePeriod", Map.of(
//				"label", range.label(),
//				"start", range.start().toString(),
//				"end", range.end().toString(),
//				"periodKey", period.name()));
//		int n = issues.size();
//		int open = 0;
//		int closed = 0;
//		int high = 0;
//		int med = 0;
//		int low = 0;
//		int sevUnk = 0;
//		List<String> blockedIds = new ArrayList<>();
//		for (Issue i : issues) {
//			if (IssueProgressUtil.isJiraBlocked(i)) {
//				String k = i.getJiraIssueKey();
//				if (k != null && !k.isBlank()) {
//					blockedIds.add(k);
//				}
//			}
//			if (IssueProgressUtil.isEffectivelyClosed(i)) {
//				closed++;
//			}
//			else {
//				open++;
//			}
//			Integer s = i.getSeverity();
//			if (s == null) {
//				sevUnk++;
//			}
//			else if (s == 1) {
//				high++;
//			}
//			else if (s == 2) {
//				med++;
//			}
//			else {
//				low++;
//			}
//		}
//		double closureRate = n == 0 ? 0.0 : Math.round(1000.0 * closed / n) / 10.0;
//		m.put("overall", Map.of(
//				"totalTickets", n,
//				"openTickets", open,
//				"closedTickets", closed,
//				"closureRatePercent", closureRate));
//		int created = 0;
//		int closedInP = 0;
//		for (Issue i : issues) {
//			if (i.getIssueDate() != null && inRange(i.getIssueDate(), range)) {
//				created++;
//			}
//			if (i.getClosingDate() != null && inRange(i.getClosingDate(), range) && IssueProgressUtil.isEffectivelyClosed(i)) {
//				closedInP++;
//			}
//		}
//		int net = created - closedInP;
//		String trendHint = n == 0
//				? "No issues in scope; no period activity."
//				: (created + closedInP) == 0
//						? "No issues created or closed in this date window."
//						: String.format(Locale.ROOT, "In this period: %d created, %d closed. Net %s%d.",
//						created, closedInP, net >= 0 ? "+" : "", net);
//		m.put("activity", Map.of(
//				"ticketsCreated", created,
//				"ticketsClosed", closedInP,
//				"trendHint", trendHint));
//		m.put("priorityBreakdown", Map.of(
//				"high", high,
//				"medium", med,
//				"low", low,
//				"unspecified", sevUnk));
//		m.put("blockedTicketIds", blockedIds.stream().sorted().toList());
//		m.put("assigneeClosedInPeriod", assigneeClosedInRange(issues, range));
//		String top = topAssigneeLine(m.getAssigneeMap());
//		m.put("topPerformerHint", top);
//		m.put("tickets", toTicketRows(issues, range));
//		m.put("truncatedTicketList", issues.size() > MAX_TICKETS_IN_JSON);
//		try {
//			return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(m.map());
//		}
//		catch (Exception e) {
//			throw new IllegalStateException("Failed to serialize report", e);
//		}
//	}

	private static Map<String, Long> assigneeClosedInRange(List<Issue> issues, ReportTimeWindows.DateRange range) {
		Map<String, Long> by = new HashMap<>();
		for (Issue i : issues) {
			if (i.getClosingDate() == null || !inRange(i.getClosingDate(), range) || !IssueProgressUtil.isEffectivelyClosed(i)) {
				continue;
			}
			String label = assigneeLabel(i);
			by.merge(label, 1L, Long::sum);
		}
		return by.entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
						.thenComparing(e -> e.getKey().toLowerCase(Locale.ROOT)))
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						Map.Entry::getValue,
						(a, b) -> a,
						LinkedHashMap::new));
	}

	private static String assigneeLabel(Issue i) {
		if (i.getAssignee() != null) {
			return coalesce(i.getAssignee().getDisplayName(), i.getAssignee().getEmail());
		}
		return coalesce(i.getJiraAssigneeDisplayName(), i.getJiraAssigneeEmail());
	}

	private static String coalesce(String a, String b) {
		if (a != null && !a.isBlank()) {
			return a.trim();
		}
		if (b != null && !b.isBlank()) {
			return b.trim();
		}
		return "Unassigned";
	}

	private static String topAssigneeLine(Map<String, Long> by) {
		if (by.isEmpty()) {
			return "No assignee had tickets closed in this period.";
		}
		long max = by.values().stream().mapToLong(Long::longValue).max().orElse(0L);
		List<String> tops = by.entrySet().stream()
				.filter(e -> e.getValue() == max)
				.map(Map.Entry::getKey)
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
		if (tops.isEmpty() || max <= 0) {
			return "No assignee had tickets closed in this period.";
		}
		if (tops.size() == 1) {
			return "Top: " + tops.getFirst() + " (" + max + " closed).";
		}
		return "Tied top (" + max + " closed each): " + String.join(", ", tops) + ".";
	}

	private List<Map<String, Object>> toTicketRows(List<Issue> issues, ReportTimeWindows.DateRange range) {
		List<Issue> sorted = issues.stream()
				.sorted(Comparator
						.comparing(Issue::getJiraIssueKey, Comparator.nullsLast(String::compareToIgnoreCase)))
				.toList();
		int cap = Math.min(sorted.size(), MAX_TICKETS_IN_JSON);
		List<Map<String, Object>> rows = new ArrayList<>(cap);
		for (int j = 0; j < cap; j++) {
			Issue i = sorted.get(j);
			rows.add(ticketRow(i, range));
		}
		return rows;
	}

	private static Map<String, Object> ticketRow(Issue i, ReportTimeWindows.DateRange range) {
		Map<String, Object> o = new LinkedHashMap<>();
		o.put("jiraKey", n(i.getJiraIssueKey()));
		o.put("title", n(i.getTitle()));
		o.put("portalStatus", i.getPortalStatus() != null ? i.getPortalStatus().name() : null);
		o.put("jiraStatus", n(i.getJiraStatus()));
		o.put("severity", i.getSeverity());
		o.put("issueDate", i.getIssueDate() != null ? i.getIssueDate().toString() : null);
		o.put("closingDate", i.getClosingDate() != null ? i.getClosingDate().toString() : null);
		o.put("assignee", assigneeLabel(i));
		o.put("category", n(i.getCategory()));
		o.put("module", n(i.getModule()));
		o.put("blocked", IssueProgressUtil.isJiraBlocked(i));
		if (i.getIssueDate() != null) {
			o.put("createdInPeriod", inRange(i.getIssueDate(), range));
		}
		if (i.getClosingDate() != null) {
			o.put("closedInPeriod", inRange(i.getClosingDate(), range) && IssueProgressUtil.isEffectivelyClosed(i));
		}
		return o;
	}

	private static String n(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static boolean inRange(LocalDate d, ReportTimeWindows.DateRange r) {
		if (d == null) {
			return false;
		}
		return !d.isBefore(r.start()) && !d.isAfter(r.end());
	}

	/** Map wrapper so we can hold a reference for top performer. */
	private static class ReportModel {
		private final Map<String, Object> map = new LinkedHashMap<>();
		private Map<String, Long> assigneeMap = Map.of();

		void put(String k, Object v) {
			if ("assigneeClosedInPeriod".equals(k) && v instanceof Map<?, ?> m) {
				@SuppressWarnings("unchecked")
				Map<String, Long> t = (Map<String, Long>) m;
				assigneeMap = t;
			}
			map.put(k, v);
		}

		Map<String, Object> map() {
			return map;
		}

		Map<String, Long> getAssigneeMap() {
			return assigneeMap;
		}
	}

}
