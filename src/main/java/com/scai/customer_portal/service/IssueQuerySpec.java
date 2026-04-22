package com.scai.customer_portal.service;

import java.time.LocalDate;

/**
 * User-filtered issue list in chat.
 */
public record IssueQuerySpec(
		Integer exactSeverity,
		boolean hasTimeWindow,
		LocalDate rangeStart,
		LocalDate rangeEnd,
		IssueQuerySpec.State state,
		WindowApply windowApply) {

	public enum State {
		ANY, OPEN, CLOSED
	}

	/**
	 * How the time window is matched when {@link #hasTimeWindow} is true.
	 */
	public enum WindowApply {
		/** do not filter by date (should not happen if hasTimeWindow) */
		NONE,
		/** issueDate in [start,end] */
		ISSUE,
		/** closingDate in [start,end] (and still “closed” for display) */
		CLOSING,
		/** issueDate in range OR closingDate in range */
		EITHER
	}
}
