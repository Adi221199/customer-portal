package com.scai.customer_portal.service;

import com.scai.customer_portal.domain.AppUser;
import com.scai.customer_portal.domain.Issue;
import com.scai.customer_portal.domain.PortalRole;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/**
 * Same visibility ordering as issue list rules (first matching role wins).
 */
public final class IssueVisibilitySpecification {

	private IssueVisibilitySpecification() {
	}

	public static Specification<Issue> visibleTo(AppUser actor) {
		return (root, query, cb) -> {
			if (actor.getRoles().contains(PortalRole.SC_ADMIN)) {
				return cb.conjunction();
			}
			if (actor.getRoles().contains(PortalRole.CUSTOMER_ADMIN) && actor.getOrganization() != null) {
				return cb.equal(root.get("organization").get("id"), actor.getOrganization().getId());
			}
			if (actor.getRoles().contains(PortalRole.CUSTOMER_USER) && actor.getOrganization() != null) {
				Join<Issue, AppUser> createdBy = root.join("createdBy", JoinType.INNER);
				Join<Issue, AppUser> assignee = root.join("assignee", JoinType.LEFT);
				Predicate sameOrg = cb.equal(root.get("organization").get("id"), actor.getOrganization().getId());
				Predicate asCreator = cb.equal(createdBy.get("id"), actor.getId());
				Predicate asAssignee = cb.and(cb.isNotNull(assignee.get("id")), cb.equal(assignee.get("id"), actor.getId()));
				Predicate asReporter = cb.equal(cb.lower(root.get("jiraReporterEmail")), actor.getEmail().toLowerCase());
				return cb.and(sameOrg, cb.or(asCreator, asAssignee, asReporter));
			}
			if (actor.getRoles().contains(PortalRole.SC_LEAD) && actor.getPods() != null && !actor.getPods().isEmpty()) {
				Join<Issue, com.scai.customer_portal.domain.Pod> pod = root.join("pod", JoinType.INNER);
				return pod.get("id").in(actor.getPods().stream().map(p -> p.getId()).toList());
			}
			if (actor.getRoles().contains(PortalRole.SC_AGENT)) {
				Join<Issue, AppUser> portalReporter = root.join("portalReporter", JoinType.LEFT);
				Predicate linked = cb.equal(portalReporter.get("id"), actor.getId());
				Predicate emailMatch = cb.equal(cb.lower(root.get("jiraReporterEmail")), actor.getEmail().toLowerCase());
				return cb.or(linked, emailMatch);
			}
			return cb.disjunction();
		};
	}
}
