package com.scai.customer_portal.service;

import com.scai.customer_portal.api.dto.dashboard.AssigneeOption;
import com.scai.customer_portal.api.dto.dashboard.DashboardAggregateResponse;
import com.scai.customer_portal.api.dto.dashboard.DashboardChartResponse;
import com.scai.customer_portal.api.dto.dashboard.DashboardFiltersResponse;
import com.scai.customer_portal.api.dto.dashboard.DashboardMetaResponse;
import com.scai.customer_portal.api.dto.dashboard.DashboardMetaResponse.DashboardChartMeta;
import com.scai.customer_portal.api.dto.dashboard.JiraTicketOption;
import com.scai.customer_portal.api.dto.dashboard.NameCountPoint;
import com.scai.customer_portal.api.dto.dashboard.OrganizationOption;
import com.scai.customer_portal.dashboard.DashboardAggregateDimension;
import com.scai.customer_portal.dashboard.DashboardChartId;
import com.scai.customer_portal.dashboard.DashboardFacet;
import com.scai.customer_portal.dashboard.DashboardFilterParams;
import com.scai.customer_portal.domain.AppUser;
import com.scai.customer_portal.domain.Issue;
import com.scai.customer_portal.domain.IssueStatus;
import com.scai.customer_portal.domain.Organization;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DashboardService {

	private static final String BLANK = "(Blank)";
	private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");
	private static final Locale EN = Locale.ENGLISH;

	@PersistenceContext
	private EntityManager entityManager;
	private final CurrentUserService currentUserService;
	private final DashboardChartAccessPolicy chartAccess;

	public DashboardService(CurrentUserService currentUserService, DashboardChartAccessPolicy chartAccess) {
		this.currentUserService = currentUserService;
		this.chartAccess = chartAccess;
	}

	public DashboardMetaResponse meta() {
		AppUser user = currentUserService.requireCurrentUser();
		Set<DashboardChartId> allowed = chartAccess.allowedCharts(user);
		List<DashboardChartMeta> charts = new ArrayList<>();
		for (DashboardChartId id : DashboardChartId.values()) {
			if (id == DashboardChartId.AGGREGATE) {
				continue;
			}
			charts.add(new DashboardChartMeta(id, id.pathSegment(), chartTitle(id), chartDescription(id)));
		}
		return new DashboardMetaResponse(charts, allowed);
	}

	public DashboardFiltersResponse filters(DashboardFilterParams params) {
		AppUser user = currentUserService.requireCurrentUser();
		return new DashboardFiltersResponse(
				distinctOrganizations(user, params),
				distinctAssignees(user, params),
				distinctSeverities(user, params),
				distinctEnvironments(user, params),
				distinctMonths(user, params),
				List.of("ALL", "HAS", "EMPTY", "NO"),
				distinctCategories(user, params),
				distinctModules(user, params),
				distinctJiraTickets(user, params),
				distinctIssueStatuses(user, params));
	}

	public DashboardChartResponse chart(DashboardChartId chartId, DashboardFilterParams params) {
		if (chartId == DashboardChartId.AGGREGATE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use GET /api/dashboard/aggregate for custom groupBy");
		}
		AppUser user = currentUserService.requireCurrentUser();
		chartAccess.requireChart(user, chartId);
		List<NameCountPoint> points = switch (chartId) {
			case ISSUES_BY_MONTH -> byMonth(user, params);
			case ISSUES_BY_CLIENT -> byClient(user, params);
			case ISSUES_BY_ENVIRONMENT -> byEnvironment(user, params);
			case ISSUES_BY_SEVERITY -> bySeverity(user, params);
			case ISSUES_BY_MODULE -> byModule(user, params);
			case ISSUES_BY_TICKET_CATEGORY -> byCategory(user, params);
			case ISSUES_BY_RCA -> byRca(user, params);
			default -> throw new IllegalStateException();
		};
		return new DashboardChartResponse(chartId, points);
	}

	public DashboardAggregateResponse aggregate(DashboardAggregateDimension dimension, DashboardFilterParams params) {
		AppUser user = currentUserService.requireCurrentUser();
		chartAccess.requireChart(user, DashboardChartId.AGGREGATE);
		List<NameCountPoint> points = switch (dimension) {
			case MONTH -> byMonth(user, params);
			case CLIENT -> byClient(user, params);
			case ENVIRONMENT -> byEnvironment(user, params);
			case SEVERITY -> bySeverity(user, params);
			case MODULE -> byModule(user, params);
			case CATEGORY -> byCategory(user, params);
			case RCA -> byRca(user, params);
		};
		return new DashboardAggregateResponse(dimension, points);
	}

	private Predicate combined(Root<Issue> root, CriteriaQuery<?> cq, CriteriaBuilder cb, AppUser user,
			DashboardFilterParams p, DashboardFacet omitFacet) {
		Predicate vis = IssueVisibilitySpecification.visibleTo(user).toPredicate(root, cq, cb);
		Predicate xf = DashboardCrossFilterSpecification.crossFilters(p, omitFacet).toPredicate(root, cq, cb);
		return cb.and(vis, xf);
	}

	private List<NameCountPoint> byMonth(AppUser user, DashboardFilterParams p) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
		Root<Issue> root = cq.from(Issue.class);
		Expression<Integer> y = cb.function("year", Integer.class, root.get("issueDate"));
		Expression<Integer> m = cb.function("month", Integer.class, root.get("issueDate"));
		cq.multiselect(y, m, cb.countDistinct(root.get("id")));
		cq.where(combined(root, cq, cb, user, p, null));
		cq.groupBy(y, m);
		cq.orderBy(cb.asc(y), cb.asc(m));
		List<Object[]> rows = entityManager.createQuery(cq).getResultList();
		List<NameCountPoint> out = new ArrayList<>();
		for (Object[] row : rows) {
			Integer year = (Integer) row[0];
			Integer month = (Integer) row[1];
			long cnt = (Long) row[2];
			if (year == null || month == null) {
				out.add(new NameCountPoint("unknown", BLANK, cnt));
			}
			else {
				LocalDate d = LocalDate.of(year, month, 1);
				String key = d.format(MONTH_KEY);
				String label = d.getMonth().getDisplayName(TextStyle.SHORT, EN) + "-" + String.format("%02d", year % 100);
				out.add(new NameCountPoint(key, label, cnt));
			}
		}
		return out;
	}

	private List<NameCountPoint> byClient(AppUser user, DashboardFilterParams p) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
		Root<Issue> root = cq.from(Issue.class);
		Join<Issue, Organization> org = root.join("organization", JoinType.INNER);
		cq.multiselect(org.get("id"), org.get("name"), cb.countDistinct(root.get("id")));
		cq.where(combined(root, cq, cb, user, p, null));
		cq.groupBy(org.get("id"), org.get("name"));
		List<NameCountPoint> clients = entityManager.createQuery(cq).getResultList().stream()
				.map(r -> new NameCountPoint(((UUID) r[0]).toString(), (String) r[1], (Long) r[2]))
				.sorted(Comparator.comparingLong(NameCountPoint::count).reversed())
				.toList();
		return clients;
	}

	private List<NameCountPoint> byEnvironment(AppUser user, DashboardFilterParams p) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
		Root<Issue> root = cq.from(Issue.class);
		Expression<String> bucket = cb.coalesce(root.get("environment"), cb.literal(""));
		cq.multiselect(bucket, cb.countDistinct(root.get("id")));
		cq.where(combined(root, cq, cb, user, p, null));
		cq.groupBy(bucket);
		List<NameCountPoint> env = mapStringBuckets(entityManager.createQuery(cq).getResultList());
		env.sort(Comparator.comparingLong(NameCountPoint::count).reversed());
		return env;
	}

	private List<NameCountPoint> bySeverity(AppUser user, DashboardFilterParams p) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
		Root<Issue> root = cq.from(Issue.class);
		cq.multiselect(root.get("severity"), cb.countDistinct(root.get("id")));
		cq.where(combined(root, cq, cb, user, p, null));
		cq.groupBy(root.get("severity"));
		cq.orderBy(cb.asc(root.get("severity")));
		List<NameCountPoint> out = new ArrayList<>();
		for (Object[] row : entityManager.createQuery(cq).getResultList()) {
			Integer s = (Integer) row[0];
			long cnt = (Long) row[1];
			String key = s == null ? "null" : s.toString();
			String label = severityLabel(s);
			out.add(new NameCountPoint(key, label, cnt));
		}
		return out;
	}

	private List<NameCountPoint> byModule(AppUser user, DashboardFilterParams p) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
		Root<Issue> root = cq.from(Issue.class);
		Expression<String> bucket = cb.coalesce(root.get("module"), cb.literal(""));
		cq.multiselect(bucket, cb.countDistinct(root.get("id")));
		cq.where(combined(root, cq, cb, user, p, null));
		cq.groupBy(bucket);
		List<NameCountPoint> mod = mapStringBuckets(entityManager.createQuery(cq).getResultList());
		mod.sort(Comparator.comparingLong(NameCountPoint::count).reversed());
		return mod;
	}

	private List<NameCountPoint> byCategory(AppUser user, DashboardFilterParams p) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
		Root<Issue> root = cq.from(Issue.class);
		Expression<String> bucket = cb.coalesce(root.get("category"), cb.literal(""));
		cq.multiselect(bucket, cb.countDistinct(root.get("id")));
		cq.where(combined(root, cq, cb, user, p, null));
		cq.groupBy(bucket);
		List<NameCountPoint> cat = mapStringBuckets(entityManager.createQuery(cq).getResultList());
		cat.sort(Comparator.comparingLong(NameCountPoint::count).reversed());
		return cat;
	}

	private List<NameCountPoint> byRca(AppUser user, DashboardFilterParams p) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
		Root<Issue> root = cq.from(Issue.class);
		Expression<String> rcaText = root.get("rcaDescription");
		Expression<String> bucket = cb.<String>selectCase()
				.when(cb.or(cb.isNull(rcaText), cb.equal(cb.length(rcaText), 0)), "BLANK")
				.when(cb.equal(cb.lower(rcaText), "no"), "NO")
				.otherwise("YES");
		cq.multiselect(bucket, cb.countDistinct(root.get("id")));
		cq.where(combined(root, cq, cb, user, p, null));
		cq.groupBy(bucket);
		List<NameCountPoint> out = new ArrayList<>();
		for (Object[] row : entityManager.createQuery(cq).getResultList()) {
			String k = (String) row[0];
			long cnt = (Long) row[1];
			String label = switch (k) {
				case "BLANK" -> BLANK;
				case "NO" -> "No";
				case "YES" -> "Yes";
				default -> k;
			};
			out.add(new NameCountPoint(k, label, cnt));
		}
		out.sort(Comparator.comparingLong(NameCountPoint::count).reversed());
		return out;
	}

	private static List<NameCountPoint> mapStringBuckets(List<Object[]> rows) {
		List<NameCountPoint> out = new ArrayList<>();
		for (Object[] row : rows) {
			String raw = (String) row[0];
			long cnt = (Long) row[1];
			boolean blank = raw == null || raw.isBlank();
			String key = blank ? DashboardCrossFilterSpecification.BLANK_TOKEN : raw;
			String label = blank ? BLANK : raw;
			out.add(new NameCountPoint(key, label, cnt));
		}
		return out;
	}

	private List<OrganizationOption> distinctOrganizations(AppUser user, DashboardFilterParams p) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> cq = cb.createTupleQuery();
		Root<Issue> root = cq.from(Issue.class);
		Join<Issue, Organization> org = root.join("organization", JoinType.INNER);
		cq.multiselect(org.get("id"), org.get("name"));
		cq.where(combined(root, cq, cb, user, p, DashboardFacet.ORGANIZATION));
		cq.distinct(true);
		cq.orderBy(cb.asc(org.get("name")));
		return entityManager.createQuery(cq).getResultList().stream()
				.map(t -> new OrganizationOption(t.get(0, UUID.class), t.get(1, String.class)))
				.toList();
	}

	private List<AssigneeOption> distinctAssignees(AppUser user, DashboardFilterParams p) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> cq = cb.createTupleQuery();
		Root<Issue> root = cq.from(Issue.class);
		Join<Issue, AppUser> a = root.join("assignee", JoinType.INNER);
		cq.multiselect(a.get("id"), a.get("email"), a.get("displayName"));
		cq.where(combined(root, cq, cb, user, p, DashboardFacet.ASSIGNEE));
		cq.distinct(true);
		cq.orderBy(cb.asc(a.get("email")));
		return entityManager.createQuery(cq).getResultList().stream()
				.map(t -> new AssigneeOption(t.get(0, UUID.class), t.get(1, String.class), t.get(2, String.class)))
				.toList();
	}

	private List<Integer> distinctSeverities(AppUser user, DashboardFilterParams p) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Integer> cq = cb.createQuery(Integer.class);
		Root<Issue> root = cq.from(Issue.class);
		cq.select(root.get("severity")).distinct(true);
		cq.where(combined(root, cq, cb, user, p, DashboardFacet.SEVERITY), cb.isNotNull(root.get("severity")));
		cq.orderBy(cb.asc(root.get("severity")));
		return entityManager.createQuery(cq).getResultList();
	}

	private List<String> distinctEnvironments(AppUser user, DashboardFilterParams p) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<String> cq = cb.createQuery(String.class);
		Root<Issue> root = cq.from(Issue.class);
		cq.select(root.get("environment")).distinct(true);
		cq.where(combined(root, cq, cb, user, p, DashboardFacet.ENVIRONMENT));
		List<String> raw = entityManager.createQuery(cq).getResultList();
		boolean hasBlank = false;
		TreeSet<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (String s : raw) {
			if (s == null || s.isBlank()) {
				hasBlank = true;
			}
			else {
				sorted.add(s);
			}
		}
		List<String> out = new ArrayList<>();
		if (hasBlank) {
			out.add(DashboardCrossFilterSpecification.BLANK_TOKEN);
		}
		out.addAll(sorted);
		return out;
	}

	private List<String> distinctMonths(AppUser user, DashboardFilterParams p) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
		Root<Issue> root = cq.from(Issue.class);
		Expression<Integer> y = cb.function("year", Integer.class, root.get("issueDate"));
		Expression<Integer> m = cb.function("month", Integer.class, root.get("issueDate"));
		cq.multiselect(y, m);
		cq.where(combined(root, cq, cb, user, p, DashboardFacet.MONTH), cb.isNotNull(root.get("issueDate")));
		cq.distinct(true);
		cq.orderBy(cb.asc(y), cb.asc(m));
		return entityManager.createQuery(cq).getResultList().stream()
				.map(r -> LocalDate.of((Integer) r[0], (Integer) r[1], 1).format(MONTH_KEY))
				.toList();
	}

	private List<String> distinctCategories(AppUser user, DashboardFilterParams p) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<String> cq = cb.createQuery(String.class);
		Root<Issue> root = cq.from(Issue.class);
		cq.select(root.get("category")).distinct(true);
		cq.where(combined(root, cq, cb, user, p, DashboardFacet.CATEGORY));
		List<String> raw = entityManager.createQuery(cq).getResultList();
		TreeSet<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (String s : raw) {
			if (s != null && !s.isBlank()) {
				sorted.add(s);
			}
		}
		List<String> out = new ArrayList<>(sorted);
		if (hasBlankCategory(user, p)) {
			out.addFirst(DashboardCrossFilterSpecification.BLANK_TOKEN);
		}
		return out;
	}

	private boolean hasBlankCategory(AppUser user, DashboardFilterParams p) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		Root<Issue> root = cq.from(Issue.class);
		cq.select(cb.countDistinct(root.get("id")));
		var cat = root.<String>get("category");
		Predicate blank = cb.or(cb.isNull(cat), cb.equal(cb.length(cat), 0));
		cq.where(combined(root, cq, cb, user, p, DashboardFacet.CATEGORY), blank);
		Long n = entityManager.createQuery(cq).getSingleResult();
		return n != null && n > 0;
	}

	private List<String> distinctModules(AppUser user, DashboardFilterParams p) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<String> cq = cb.createQuery(String.class);
		Root<Issue> root = cq.from(Issue.class);
		cq.select(root.get("module")).distinct(true);
		cq.where(combined(root, cq, cb, user, p, DashboardFacet.MODULE));
		List<String> raw = entityManager.createQuery(cq).getResultList();
		TreeSet<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		boolean blank = false;
		for (String s : raw) {
			if (s == null || s.isBlank()) {
				blank = true;
			}
			else {
				sorted.add(s);
			}
		}
		List<String> out = new ArrayList<>();
		if (blank) {
			out.add(DashboardCrossFilterSpecification.BLANK_TOKEN);
		}
		out.addAll(sorted);
		return out;
	}

	private List<String> distinctIssueStatuses(AppUser user, DashboardFilterParams p) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<IssueStatus> cq = cb.createQuery(IssueStatus.class);
		Root<Issue> root = cq.from(Issue.class);
		cq.select(root.get("portalStatus")).distinct(true);
		cq.where(combined(root, cq, cb, user, p, DashboardFacet.PORTAL_STATUS));
		cq.orderBy(cb.asc(root.get("portalStatus")));
		return entityManager.createQuery(cq).getResultList().stream().map(IssueStatus::name).toList();
	}

	private List<JiraTicketOption> distinctJiraTickets(AppUser user, DashboardFilterParams p) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> cq = cb.createTupleQuery();
		Root<Issue> root = cq.from(Issue.class);
		cq.multiselect(root.get("jiraIssueKey"), root.get("title"));
		cq.where(combined(root, cq, cb, user, p, DashboardFacet.JIRA_KEY), cb.isNotNull(root.get("jiraIssueKey")));
		cq.distinct(true);
		cq.orderBy(cb.asc(root.get("jiraIssueKey")));
		return entityManager.createQuery(cq).setMaxResults(200).getResultList().stream()
				.map(t -> new JiraTicketOption(t.get(0, String.class), t.get(1, String.class)))
				.toList();
	}

	private static String severityLabel(Integer s) {
		if (s == null) {
			return BLANK;
		}
		return switch (s) {
			case 1 -> "Severity 1 (High)";
			case 2 -> "Severity 2 (Moderate)";
			case 3 -> "Severity 3 (Minor)";
			default -> "Severity " + s;
		};
	}

	private static String chartTitle(DashboardChartId id) {
		return switch (id) {
			case ISSUES_BY_MONTH -> "Issue count by month";
			case ISSUES_BY_CLIENT -> "Issue count by client";
			case ISSUES_BY_ENVIRONMENT -> "Issue count by environment";
			case ISSUES_BY_SEVERITY -> "Issue count by severity";
			case ISSUES_BY_MODULE -> "Issue count by module";
			case ISSUES_BY_TICKET_CATEGORY -> "Issue count by ticket category";
			case ISSUES_BY_RCA -> "Issue count by RCA";
			case AGGREGATE -> "Aggregate";
		};
	}

	private static String chartDescription(DashboardChartId id) {
		return switch (id) {
			case ISSUES_BY_MONTH -> "Trend by issue date (calendar month).";
			case ISSUES_BY_CLIENT -> "By portal organization (Jira Customer).";
			case ISSUES_BY_ENVIRONMENT -> "By environment / Env field.";
			case ISSUES_BY_SEVERITY -> "1=High, 2=Moderate, 3=Minor.";
			case ISSUES_BY_MODULE -> "By module / components.";
			case ISSUES_BY_TICKET_CATEGORY -> "By category / issue metadata.";
			case ISSUES_BY_RCA -> "Yes / No / Blank from RCA text.";
			case AGGREGATE -> "Custom groupBy for drill-down.";
		};
	}
}
