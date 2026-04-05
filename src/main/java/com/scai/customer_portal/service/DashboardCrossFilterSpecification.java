package com.scai.customer_portal.service;

import com.scai.customer_portal.dashboard.DashboardFacet;
import com.scai.customer_portal.dashboard.DashboardFilterParams;
import com.scai.customer_portal.dashboard.RcaFilter;
import com.scai.customer_portal.domain.Issue;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies dashboard slicers. When computing distinct values for facet {@code omit}, that facet's filter is skipped.
 */
public final class DashboardCrossFilterSpecification {

	public static final String BLANK_TOKEN = "__BLANK__";

	private DashboardCrossFilterSpecification() {
	}

	public static Specification<Issue> crossFilters(DashboardFilterParams p, DashboardFacet omit) {
		return (root, query, cb) -> {
			List<Predicate> ps = new ArrayList<>();
			if (shouldApply(DashboardFacet.ORGANIZATION, omit) && p.organizationId() != null) {
				ps.add(cb.equal(root.get("organization").get("id"), p.organizationId()));
			}
			if (shouldApply(DashboardFacet.ASSIGNEE, omit) && p.assigneeId() != null) {
				ps.add(cb.equal(root.get("assignee").get("id"), p.assigneeId()));
			}
			if (shouldApply(DashboardFacet.SEVERITY, omit) && p.severity() != null) {
				ps.add(cb.equal(root.get("severity"), p.severity()));
			}
			if (shouldApply(DashboardFacet.ENVIRONMENT, omit) && p.environment() != null && !p.environment().isBlank()) {
				ps.add(envPredicate(root, cb, p.environment()));
			}
			if (shouldApply(DashboardFacet.MONTH, omit) && p.month() != null && !p.month().isBlank()) {
				try {
					YearMonth ym = YearMonth.parse(p.month().trim());
					LocalDate start = ym.atDay(1);
					LocalDate end = ym.atEndOfMonth();
					ps.add(cb.between(root.get("issueDate"), start, end));
				}
				catch (Exception ignored) {
					// invalid month → no rows
					ps.add(cb.disjunction());
				}
			}
			if (shouldApply(DashboardFacet.RCA, omit) && p.rca() != null && p.rca() != RcaFilter.ALL) {
				ps.add(rcaPredicate(root, cb, p.rca()));
			}
			if (shouldApply(DashboardFacet.CATEGORY, omit) && p.category() != null && !p.category().isBlank()) {
				ps.add(categoryPredicate(root, cb, p.category()));
			}
			if (shouldApply(DashboardFacet.MODULE, omit) && p.module() != null && !p.module().isBlank()) {
				ps.add(modulePredicate(root, cb, p.module()));
			}
			if (shouldApply(DashboardFacet.JIRA_KEY, omit) && p.jiraKey() != null && !p.jiraKey().isBlank()) {
				ps.add(cb.equal(root.get("jiraIssueKey"), p.jiraKey().trim()));
			}
			return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(Predicate[]::new));
		};
	}

	private static boolean shouldApply(DashboardFacet facet, DashboardFacet omit) {
		return omit == null || facet != omit;
	}

	private static Predicate envPredicate(jakarta.persistence.criteria.Root<Issue> root,
			jakarta.persistence.criteria.CriteriaBuilder cb, String raw) {
		var env = root.<String>get("environment");
		if (BLANK_TOKEN.equalsIgnoreCase(raw.trim())) {
			return cb.or(cb.isNull(env), cb.equal(cb.length(env), 0));
		}
		return cb.equal(env, raw.trim());
	}

	private static Predicate categoryPredicate(jakarta.persistence.criteria.Root<Issue> root,
			jakarta.persistence.criteria.CriteriaBuilder cb, String raw) {
		var cat = root.<String>get("category");
		if (BLANK_TOKEN.equalsIgnoreCase(raw.trim())) {
			return cb.or(cb.isNull(cat), cb.equal(cb.length(cat), 0));
		}
		return cb.equal(cat, raw.trim());
	}

	private static Predicate modulePredicate(jakarta.persistence.criteria.Root<Issue> root,
			jakarta.persistence.criteria.CriteriaBuilder cb, String raw) {
		var mod = root.<String>get("module");
		if (BLANK_TOKEN.equalsIgnoreCase(raw.trim())) {
			return cb.or(cb.isNull(mod), cb.equal(cb.length(mod), 0));
		}
		return cb.equal(mod, raw.trim());
	}

	private static Predicate rcaPredicate(jakarta.persistence.criteria.Root<Issue> root,
			jakarta.persistence.criteria.CriteriaBuilder cb, RcaFilter rca) {
		var rcaText = root.<String>get("rcaDescription");
		return switch (rca) {
			case ALL -> cb.conjunction();
			case HAS -> cb.and(cb.isNotNull(rcaText), cb.greaterThan(cb.length(rcaText), 0));
			case EMPTY -> cb.or(cb.isNull(rcaText), cb.equal(cb.length(rcaText), 0));
			case NO -> cb.equal(cb.lower(rcaText), "no");
		};
	}
}
