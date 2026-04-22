package com.scai.customer_portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scai.customer_portal.api.dto.ChatRequest;
import com.scai.customer_portal.api.dto.ChatResponse;
import com.scai.customer_portal.api.dto.dashboard.AssigneeOption;
import com.scai.customer_portal.api.dto.dashboard.DashboardFiltersResponse;
import com.scai.customer_portal.api.dto.dashboard.JiraTicketOption;
import com.scai.customer_portal.api.dto.dashboard.OrganizationOption;
import com.scai.customer_portal.dashboard.DashboardFilterParams;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatService {

	private static final List<String> FILTER_KEYS = List.of(
			"organizationId",
			"spocUserId",
			"severity",
			"environment",
			"month",
			"rca",
			"category",
			"module",
			"jiraKey",
			"portalStatus");

	private final NvidiaNimService nvidiaNimService;
	private final IssueService issueService;
	private final DashboardService dashboardService;
	private final ObjectMapper objectMapper;

	public ChatService(
			NvidiaNimService nvidiaNimService,
			IssueService issueService,
			DashboardService dashboardService,
			ObjectMapper objectMapper) {
		this.nvidiaNimService = nvidiaNimService;
		this.issueService = issueService;
		this.dashboardService = dashboardService;
		this.objectMapper = objectMapper;
	}

	public ChatResponse handle(ChatRequest request) throws Exception {
		if (!nvidiaNimService.isConfigured()) {
			return ChatResponse.textOnly(
					"The assistant is not configured: set NVIDIA_API_KEY (or nvidia.api.key) on the server to enable natural-language answers and dashboard filters.");
		}
		String message = request.message() == null ? "" : request.message().trim();
		if (message.isEmpty()) {
			return ChatResponse.textOnly("Please enter a message.");
		}
		DashboardFilterParams ctx = dashboardParamsFromChatFilters(request.currentDashboardFilters());
		DashboardFiltersResponse facets = dashboardService.filters(ctx);
		String facetPrompt = buildFacetCatalog(facets);
		String currentJson = objectMapper.writeValueAsString(
				normalizeFilterMap(request.currentDashboardFilters()));
		String system = """
				You are Intelli Desk, an assistant for a Jira-backed customer portal with analytics dashboards.

				Your job: interpret the user message and output ONLY a single JSON object (no markdown fences, no commentary) with exactly these keys:
				- "intent": one of
				  "ticket_status" — user asks status/details of a specific Jira key like DPAI-123 or EDM-3617
				  "completed_this_week" — tickets completed this calendar week
				  "blocked_tickets" — blocked tickets
				  "high_priority" — severity 1 / critical / highest
				  "apply_dashboard_filters" — user wants to narrow or change dashboard data using slicers (client, SPOC, severity, environment, month, RCA, category, module, Jira key, portal status)
				  "general" — anything else (greeting, how-to, unrelated)
				- "ticketId": Jira key string or null (for ticket_status only)
				- "dashboardFilters": object whose keys are exactly:
				  organizationId, spocUserId, severity, environment, month, rca, category, module, jiraKey, portalStatus
				  Each value is an array of strings (possibly empty). Use ONLY values that appear in the FACET CATALOG below.
				  For organizationId use the UUID from the catalog. For spocUserId use either the UUID or the email string from the catalog.
				  For severity use string digits like "1","2" as in the catalog.
				  For RCA use only: HAS, EMPTY, NO, ALL (prefer HAS/EMPTY/NO; ALL clears RCA filter).
				  For portalStatus use enum names like OPEN, IN_PROGRESS, DONE as listed.
				- "replaceFilters": boolean — true means replace all slicers with dashboardFilters; false means merge: start from CURRENT DASHBOARD FILTERS and overlay non-empty arrays from dashboardFilters (empty array in dashboardFilters means "leave that dimension unchanged" when replaceFilters is false).
				- "reply": short user-facing message (1–3 sentences). For structured intents except general, you may set a brief intro; the system may replace with data.

				CURRENT DASHBOARD FILTERS (JSON):
				""" + currentJson + "\n\nFACET CATALOG (pick only from these; never invent UUIDs or keys):\n" + facetPrompt;
		String raw = nvidiaNimService.chatCompletion(system, "User message:\n" + message, 1400, 0.15);
		JsonNode node;
		try {
			node = objectMapper.readTree(extractJsonObject(raw));
		}
		catch (Exception e) {
			return ChatResponse.textOnly(
					"I could not parse a structured reply from the model. Try asking in shorter sentences, or rephrase. ("
							+ e.getMessage() + ")");
		}
		String intent = node.path("intent").asText("general").trim().toLowerCase(Locale.ROOT);
		String ticketId = node.path("ticketId").asText(null);
		if (ticketId != null && ticketId.isBlank()) {
			ticketId = null;
		}
		String aiReply = node.path("reply").asText("").trim();
		boolean replace = node.path("replaceFilters").asBoolean(true);
		JsonNode filtersNode = node.path("dashboardFilters");
		Map<String, List<String>> sanitized = sanitizeDashboardFilters(filtersNode, facets);
		Map<String, List<String>> merged = mergeFilters(request.currentDashboardFilters(), sanitized, replace);

		switch (intent) {
			case "ticket_status" -> {
				if (ticketId == null) {
					return ChatResponse.textOnly(
							aiReply.isEmpty()
									? "Which Jira ticket should I look up? Use a key like DPAI-123."
									: aiReply);
				}
				String data = issueService.getTicketStatus(ticketId);
				return ChatResponse.textOnly(aiReply.isEmpty() ? data : aiReply + "\n\n" + data);
			}
			case "completed_this_week" -> {
				String data = issueService.getCompletedThisWeek();
				return ChatResponse.textOnly(aiReply.isEmpty() ? data : aiReply + "\n\n" + data);
			}
			case "blocked_tickets" -> {
				String data = issueService.getBlockedTickets();
				return ChatResponse.textOnly(aiReply.isEmpty() ? data : aiReply + "\n\n" + data);
			}
			case "high_priority" -> {
				String data = issueService.getHighPriorityIssues();
				return ChatResponse.textOnly(aiReply.isEmpty() ? data : aiReply + "\n\n" + data);
			}
			case "apply_dashboard_filters" -> {
				boolean hasAny = merged.values().stream().anyMatch(list -> list != null && !list.isEmpty());
				if (!hasAny) {
					return ChatResponse.textOnly(
							aiReply.isEmpty()
									? "I did not find any matching filter values in your data. Try naming a client, month, or status from your dashboard dropdowns."
									: aiReply);
				}
				String ack = aiReply.isEmpty()
						? "Applied dashboard filters. Opening Analytics with your selections."
						: aiReply;
				return new ChatResponse(ack, merged, replace, true);
			}
			default -> {
				if (!aiReply.isEmpty()) {
					return ChatResponse.textOnly(aiReply);
				}
				String general = nvidiaNimService.chatCompletion(
						"You are Intelli Desk: helpful, concise (under 120 words), professional. "
								+ "The portal has Issues, Analytics dashboard with cross-filters, and Jira sync.",
						message,
						900,
						0.6);
				return ChatResponse.textOnly(general);
			}
		}
	}

	private static DashboardFilterParams dashboardParamsFromChatFilters(Map<String, List<String>> raw) {
		if (raw == null || raw.isEmpty()) {
			return DashboardFilterParams.empty();
		}
		List<UUID> orgIds = parseUuidList(raw.get("organizationId"));
		List<String> spocEmails = new ArrayList<>();
		List<UUID> onlyUuids = new ArrayList<>();
		if (raw.get("spocUserId") != null) {
			for (String s : raw.get("spocUserId")) {
				if (s == null || s.isBlank()) {
					continue;
				}
				try {
					onlyUuids.add(UUID.fromString(s.trim()));
				}
				catch (IllegalArgumentException e) {
					spocEmails.add(s.trim());
				}
			}
		}
		List<Integer> severities = new ArrayList<>();
		if (raw.get("severity") != null) {
			for (String s : raw.get("severity")) {
				if (s == null || s.isBlank()) {
					continue;
				}
				try {
					severities.add(Integer.parseInt(s.trim()));
				}
				catch (NumberFormatException ignored) {
					/* skip */
				}
			}
		}
		List<String> env = trimCopy(raw.get("environment"));
		List<String> month = trimCopy(raw.get("month"));
		List<String> rca = trimCopy(raw.get("rca"));
		List<String> cat = trimCopy(raw.get("category"));
		List<String> mod = trimCopy(raw.get("module"));
		List<String> keys = trimCopy(raw.get("jiraKey"));
		List<com.scai.customer_portal.domain.IssueStatus> statuses = new ArrayList<>();
		if (raw.get("portalStatus") != null) {
			for (String s : raw.get("portalStatus")) {
				if (s == null || s.isBlank()) {
					continue;
				}
				try {
					statuses.add(com.scai.customer_portal.domain.IssueStatus.valueOf(s.trim().toUpperCase(Locale.ROOT)));
				}
				catch (IllegalArgumentException ignored) {
					/* skip */
				}
			}
		}
		return new DashboardFilterParams(
				orgIds.isEmpty() ? null : orgIds,
				onlyUuids.isEmpty() ? null : onlyUuids,
				spocEmails.isEmpty() ? null : spocEmails,
				severities.isEmpty() ? null : severities,
				env.isEmpty() ? null : env,
				month.isEmpty() ? null : month,
				rca == null || rca.isEmpty() ? null : rca.stream().map(com.scai.customer_portal.dashboard.RcaFilter::parse)
						.filter(r -> r != com.scai.customer_portal.dashboard.RcaFilter.ALL).distinct().toList(),
				cat.isEmpty() ? null : cat,
				mod.isEmpty() ? null : mod,
				keys.isEmpty() ? null : keys,
				statuses.isEmpty() ? null : statuses);
	}

	private static List<UUID> parseUuidList(List<String> in) {
		if (in == null) {
			return List.of();
		}
		List<UUID> out = new ArrayList<>();
		for (String s : in) {
			if (s == null || s.isBlank()) {
				continue;
			}
			try {
				out.add(UUID.fromString(s.trim()));
			}
			catch (IllegalArgumentException ignored) {
				/* skip */
			}
		}
		return out;
	}

	private static List<String> trimCopy(List<String> in) {
		if (in == null) {
			return List.of();
		}
		return in.stream()
				.filter(s -> s != null && !s.isBlank())
				.map(String::trim)
				.toList();
	}

	private Map<String, List<String>> normalizeFilterMap(Map<String, List<String>> in) {
		Map<String, List<String>> out = new LinkedHashMap<>();
		for (String key : FILTER_KEYS) {
			List<String> v = in == null ? List.of() : in.getOrDefault(key, List.of());
			out.put(key, v == null ? new ArrayList<>() : new ArrayList<>(v.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList()));
		}
		return out;
	}

	private Map<String, List<String>> mergeFilters(
			Map<String, List<String>> currentRaw,
			Map<String, List<String>> patchSanitized,
			boolean replace) {
		Map<String, List<String>> cur = normalizeFilterMap(currentRaw);
		if (replace) {
			Map<String, List<String>> out = new LinkedHashMap<>();
			for (String key : FILTER_KEYS) {
				out.put(key, new ArrayList<>(patchSanitized.getOrDefault(key, List.of())));
			}
			return out;
		}
		Map<String, List<String>> out = new LinkedHashMap<>();
		for (String key : FILTER_KEYS) {
			List<String> p = patchSanitized.get(key);
			if (p != null && !p.isEmpty()) {
				out.put(key, new ArrayList<>(p));
			}
			else {
				out.put(key, new ArrayList<>(cur.getOrDefault(key, List.of())));
			}
		}
		return out;
	}

	private Map<String, List<String>> sanitizeDashboardFilters(JsonNode filtersNode, DashboardFiltersResponse facets) {
		Map<String, List<String>> out = new LinkedHashMap<>();
		for (String key : FILTER_KEYS) {
			out.put(key, new ArrayList<>());
		}
		if (filtersNode == null || !filtersNode.isObject()) {
			return out;
		}
		Set<String> orgAllowed = facets.clients().stream().map(o -> o.id().toString()).collect(Collectors.toCollection(LinkedHashSet::new));
		for (String id : readStringArray(filtersNode.path("organizationId"))) {
			if (orgAllowed.contains(id)) {
				out.get("organizationId").add(id);
			}
		}
		Set<String> spocUuid = new LinkedHashSet<>();
		Set<String> spocEmail = new LinkedHashSet<>();
		for (AssigneeOption a : facets.spocs()) {
			if (a.id() != null) {
				spocUuid.add(a.id().toString());
			}
			if (a.email() != null && !a.email().isBlank()) {
				spocEmail.add(a.email().trim().toLowerCase(Locale.ROOT));
			}
		}
		for (String s : readStringArray(filtersNode.path("spocUserId"))) {
			if (spocUuid.contains(s)) {
				out.get("spocUserId").add(s);
			}
			else if (spocEmail.contains(s.toLowerCase(Locale.ROOT))) {
				// store original casing from input if matches email
				out.get("spocUserId").add(s);
			}
		}
		Set<String> sevAllowed = facets.severities().stream().map(i -> Integer.toString(i)).collect(Collectors.toCollection(LinkedHashSet::new));
		for (String s : readStringArray(filtersNode.path("severity"))) {
			String t = s.trim();
			if (sevAllowed.contains(t)) {
				out.get("severity").add(t);
			}
		}
		Set<String> envAllowed = new LinkedHashSet<>(facets.environments());
		for (String s : readStringArray(filtersNode.path("environment"))) {
			if (envAllowed.contains(s)) {
				out.get("environment").add(s);
			}
		}
		Set<String> monthAllowed = new LinkedHashSet<>(facets.months());
		for (String s : readStringArray(filtersNode.path("month"))) {
			if (monthAllowed.contains(s)) {
				out.get("month").add(s);
			}
		}
		Set<String> rcaAllowed = new LinkedHashSet<>(facets.rcaOptions());
		for (String s : readStringArray(filtersNode.path("rca"))) {
			String u = s.trim().toUpperCase(Locale.ROOT);
			if (rcaAllowed.contains(u)) {
				out.get("rca").add(u);
			}
		}
		Set<String> catAllowed = new LinkedHashSet<>(facets.ticketCategories());
		for (String s : readStringArray(filtersNode.path("category"))) {
			if (catAllowed.contains(s)) {
				out.get("category").add(s);
			}
		}
		Set<String> modAllowed = new LinkedHashSet<>(facets.modules());
		for (String s : readStringArray(filtersNode.path("module"))) {
			if (modAllowed.contains(s)) {
				out.get("module").add(s);
			}
		}
		Set<String> jiraAllowed = facets.jiraTickets().stream().map(JiraTicketOption::jiraKey).collect(Collectors.toCollection(LinkedHashSet::new));
		for (String s : readStringArray(filtersNode.path("jiraKey"))) {
			if (jiraAllowed.contains(s)) {
				out.get("jiraKey").add(s);
			}
		}
		Set<String> stAllowed = facets.issueStatuses().stream().map(x -> x.toUpperCase(Locale.ROOT)).collect(Collectors.toCollection(LinkedHashSet::new));
		for (String s : readStringArray(filtersNode.path("portalStatus"))) {
			String u = s.trim().toUpperCase(Locale.ROOT);
			if (stAllowed.contains(u)) {
				out.get("portalStatus").add(u);
			}
		}
		return out;
	}

	private static List<String> readStringArray(JsonNode arr) {
		List<String> out = new ArrayList<>();
		if (arr == null || !arr.isArray()) {
			return out;
		}
		for (JsonNode n : arr) {
			if (n.isTextual()) {
				String t = n.asText("").trim();
				if (!t.isEmpty()) {
					out.add(t);
				}
			}
		}
		return out;
	}

	private static String buildFacetCatalog(DashboardFiltersResponse f) {
		StringBuilder sb = new StringBuilder(4096);
		sb.append("Organizations (id | name):\n");
		int n = 0;
		for (OrganizationOption o : f.clients()) {
			sb.append(o.id()).append(" | ").append(esc(o.name())).append('\n');
			if (++n >= 120) {
				break;
			}
		}
		sb.append("\nSPOCs (id or email | display):\n");
		n = 0;
		for (AssigneeOption a : f.spocs()) {
			String idPart = a.id() != null ? a.id().toString() : a.email();
			sb.append(idPart).append(" | ").append(esc(a.displayName())).append('\n');
			if (++n >= 80) {
				break;
			}
		}
		sb.append("\nSeverities (integer strings): ").append(f.severities().stream().map(String::valueOf).collect(Collectors.joining(", "))).append('\n');
		sb.append("\nEnvironments (sample):\n");
		appendLimited(sb, f.environments(), 60);
		sb.append("\nMonths (yyyy-MM):\n");
		appendLimited(sb, f.months(), 48);
		sb.append("\nRCA options: ").append(String.join(", ", f.rcaOptions())).append('\n');
		sb.append("\nCategories (sample):\n");
		appendLimited(sb, f.ticketCategories(), 50);
		sb.append("\nModules (sample):\n");
		appendLimited(sb, f.modules(), 50);
		sb.append("\nJira tickets (key — title, sample):\n");
		n = 0;
		for (JiraTicketOption j : f.jiraTickets()) {
			sb.append(j.jiraKey()).append(" — ").append(esc(j.title())).append('\n');
			if (++n >= 80) {
				break;
			}
		}
		sb.append("\nPortal statuses: ").append(String.join(", ", f.issueStatuses())).append('\n');
		return sb.toString();
	}

	private static void appendLimited(StringBuilder sb, List<String> items, int max) {
		int c = 0;
		for (String s : items) {
			sb.append(esc(s)).append('\n');
			if (++c >= max) {
				break;
			}
		}
	}

	private static String esc(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("\n", " ").trim();
	}

	private static String stripMarkdownFence(String text) {
		if (text == null) {
			return "";
		}
		String t = text.trim();
		if (t.startsWith("```")) {
			int nl = t.indexOf('\n');
			if (nl > 0) {
				t = t.substring(nl + 1);
			}
			int end = t.lastIndexOf("```");
			if (end >= 0) {
				t = t.substring(0, end);
			}
		}
		return t.trim();
	}

	private static String extractJsonObject(String raw) {
		String t = stripMarkdownFence(raw);
		int start = t.indexOf('{');
		if (start < 0) {
			return t;
		}
		int depth = 0;
		for (int i = start; i < t.length(); i++) {
			char c = t.charAt(i);
			if (c == '{') {
				depth++;
			}
			else if (c == '}') {
				depth--;
				if (depth == 0) {
					return t.substring(start, i + 1);
				}
			}
		}
		return t;
	}
}
