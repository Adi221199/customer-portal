package com.scai.customer_portal.service;

import com.scai.customer_portal.domain.Issue;
import com.scai.customer_portal.domain.IssueStatus;

import java.util.Locale;

/**
 * Shared “closed / blocked” checks for Jira + portal, used by reports and chat filters.
 */
public final class IssueProgressUtil {

	private IssueProgressUtil() {
	}

	public static boolean isJiraBlocked(Issue i) {
		return i.getJiraStatus() != null && "blocked".equalsIgnoreCase(i.getJiraStatus().trim());
	}

	/** Aligned with {@link com.scai.customer_portal.service.JiraIssueMapper#mapPortalStatus(String)}. */
	public static boolean isEffectivelyClosed(Issue i) {
		if (i.getPortalStatus() == IssueStatus.CLOSED) {
			return true;
		}
		if (i.getPortalStatus() == IssueStatus.RESOLVED) {
			return true;
		}
		String j = i.getJiraStatus();
		if (j == null) {
			return false;
		}
		String s = j.toLowerCase(Locale.ROOT);
		if (s.contains("incomplete") || s.contains("unresolved")) {
			return false;
		}
		return s.contains("done")
				|| s.contains("closed")
				|| s.contains("resolved")
				|| s.contains("complete");
	}
}
