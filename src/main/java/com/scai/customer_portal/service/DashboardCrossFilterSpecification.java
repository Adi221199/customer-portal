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
 * Dashboard slicers. When building distinct values for facet {@code omit}, that facet's filter is omitted so the
 * dropdown still shows alternatives; <strong>all other</strong> active filters (including chart drill params) still apply.
 */
public final class DashboardCrossFilterSpecification {

	public static final String BLANK_TOKEN = "__BLANK__";

	private DashboardCrossFilterSpecification() {
	}

	public static Specification<Issue> crossFilters(DashboardFilterParams p, DashboardFacet omit) {
		return (root, query, cb) -> {
			List<Predicate> ps = new ArrayList<>();
			if (shouldApply(DashboardFacet.ORGANIZATION, omit) && p.organizationIds() != null && !p.organizationIds().isEmpty()) {
				ps.add(root.get("organization").get("id").in(p.organizationIds()));
			}
			if (shouldApply(DashboardFacet.ASSIGNEE, omit) && p.assigneeIds() != null && !p.assigneeIds().isEmpty()) {
				ps.add(root.get("assignee").get("id").in(p.assigneeIds()));
			}
			if (shouldApply(DashboardFacet.SEVERITY, omit) && p.severities() != null && !p.severities().isEmpty()) {
				ps.add(root.get("severity").in(p.severities()));
			}
			if (shouldApply(DashboardFacet.ENVIRONMENT, omit) && p.environments() != null && !p.environments().isEmpty()) {
				List<Predicate> envPs = new ArrayList<>();
				for (String raw : p.environments()) {
					if (raw == null || raw.isBlank()) {
						continue;
					}
					envPs.add(envPredicate(root, cb, raw));
				}
				if (!envPs.isEmpty()) {
					ps.add(cb.or(envPs.toArray(Predicate[]::new)));
				}
			}
			if (shouldApply(DashboardFacet.MONTH, omit) && p.months() != null && !p.months().isEmpty()) {
				List<Predicate> monthPs = new ArrayList<>();
				for (String m : p.months()) {
					if (m == null || m.isBlank()) {
						continue;
					}
					try {
						YearMonth ym = YearMonth.parse(m.trim());
						LocalDate start = ym.atDay(1);
						LocalDate end = ym.atEndOfMonth();
						monthPs.add(cb.between(root.get("issueDate"), start, end));
					}
					catch (Exception ignored) {
						// skip invalid
					}
				}
				if (!monthPs.isEmpty()) {
					ps.add(cb.or(monthPs.toArray(Predicate[]::new)));
				}
			}
			if (shouldApply(DashboardFacet.RCA, omit) && p.rcaFilters() != null && !p.rcaFilters().isEmpty()) {
				List<RcaFilter> nonAll = p.rcaFilters().stream().filter(r -> r != RcaFilter.ALL).distinct().toList();
				if (!nonAll.isEmpty()) {
					List<Predicate> rcaPs = nonAll.stream().map(r -> rcaPredicate(root, cb, r)).toList();
					ps.add(cb.or(rcaPs.toArray(Predicate[]::new)));
				}
			}
			if (shouldApply(DashboardFacet.CATEGORY, omit) && p.categories() != null && !p.categories().isEmpty()) {
				List<Predicate> catPs = new ArrayList<>();
				for (String raw : p.categories()) {
					if (raw == null || raw.isBlank()) {
						continue;
					}
					catPs.add(categoryPredicate(root, cb, raw));
				}
				if (!catPs.isEmpty()) {
					ps.add(cb.or(catPs.toArray(Predicate[]::new)));
				}
			}
			if (shouldApply(DashboardFacet.MODULE, omit) && p.modules() != null && !p.modules().isEmpty()) {
				List<Predicate> modPs = new ArrayList<>();
				for (String raw : p.modules()) {
					if (raw == null || raw.isBlank()) {
						continue;
					}
					modPs.add(modulePredicate(root, cb, raw));
				}
				if (!modPs.isEmpty()) {
					ps.add(cb.or(modPs.toArray(Predicate[]::new)));
				}
			}
			if (shouldApply(DashboardFacet.JIRA_KEY, omit) && p.jiraKeys() != null && !p.jiraKeys().isEmpty()) {
				List<String> keys = p.jiraKeys().stream()
						.map(String::trim)
						.filter(s -> !s.isBlank())
						.distinct()
						.toList();
				if (!keys.isEmpty()) {
					ps.add(root.get("jiraIssueKey").in(keys));
				}
			}
			if (shouldApply(DashboardFacet.PORTAL_STATUS, omit) && p.portalStatuses() != null && !p.portalStatuses().isEmpty()) {
				ps.add(root.get("portalStatus").in(p.portalStatuses()));
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
