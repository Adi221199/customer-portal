package com.scai.customer_portal.service;

import com.scai.customer_portal.domain.AppUser;
import com.scai.customer_portal.domain.Issue;
import com.scai.customer_portal.domain.IssueStatus;
import com.scai.customer_portal.domain.PortalRole;

/**
 * Enforces Open → … → Closed. {@link PortalRole#SC_ADMIN} may also move status backward. Customers cannot change
 * status in PATCH (enforced in {@code canUpdatePortalStatus} as well).
 */
public final class PortalIssueStatusWorkflow {

	private PortalIssueStatusWorkflow() {
	}

	public static void validateTransition(AppUser actor, IssueStatus from, IssueStatus to) {
		if (to == null) {
			throw new IllegalArgumentException("portalStatus is required when provided");
		}
		if (to == from) {
			return;
		}
		if (isCustomerUserOnly(actor)) {
			throw new SecurityException("Not allowed to change issue status");
		}
		boolean scAdmin = actor.getRoles().contains(PortalRole.SC_ADMIN);
		if (to.ordinal() < from.ordinal() && !scAdmin) {
			throw new IllegalArgumentException("Status cannot go backwards (only an SC admin can move a ticket to an earlier step)");
		}
		if (scAdmin) {
			return;
		}
		if (to.ordinal() > from.ordinal() + 1) {
			throw new IllegalArgumentException(
					"Invalid status transition: use one step at a time. Current: " + from + ", requested: " + to);
		}
	}

	private static boolean isCustomerUserOnly(AppUser actor) {
		return actor.getRoles().size() == 1 && actor.getRoles().contains(PortalRole.CUSTOMER_USER);
	}

	public static String jiraStyleLabel(IssueStatus s) {
		if (s == null) {
			return "Unknown";
		}
		return switch (s) {
			case OPEN -> "Open";
			case ACKNOWLEDGED -> "Acknowledged";
			case IN_PROGRESS -> "In Progress";
			case RESOLVED -> "Resolved";
			case CLOSED -> "Closed";
		};
	}

	public static void setClosingDateWhenClosed(Issue issue) {
		if (issue.getPortalStatus() == IssueStatus.CLOSED && issue.getClosingDate() == null) {
			issue.setClosingDate(java.time.LocalDate.now());
		}
	}
}
